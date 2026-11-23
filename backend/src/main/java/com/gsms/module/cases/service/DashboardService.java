package com.gsms.module.cases.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsms.common.CryptoUtil;
import com.gsms.common.LoginUser;
import com.gsms.common.UserContext;
import com.gsms.module.cases.dto.DashboardVO;
import com.gsms.module.cases.entity.WfNodeDef;
import com.gsms.module.report.mapper.ReportMapper;
import com.gsms.module.system.entity.SysUser;
import com.gsms.module.system.mapper.SysUserMapper;
import com.gsms.module.system.service.DeptService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程总览。统计口径与报表中心一致，复用 ReportMapper 的 SQL 与数据范围片段，
 * 避免两处权限逻辑漂移。
 */
@Service
public class DashboardService {

    private static final String ROLE_AUDITOR = "ROLE_AUDITOR";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Map<String, String> FLOW_NAME = new LinkedHashMap<>();
    static {
        FLOW_NAME.put("INJURY", "工伤案件");
        FLOW_NAME.put("ARBITRATION", "劳动仲裁案件");
    }

    private final ReportMapper reportMapper;
    private final WorkflowFlowService flowService;
    private final SysUserMapper userMapper;
    private final DeptService deptService;
    private final CaseStateMachine stateMachine;

    public DashboardService(ReportMapper reportMapper, WorkflowFlowService flowService,
                            SysUserMapper userMapper, DeptService deptService,
                            CaseStateMachine stateMachine) {
        this.reportMapper = reportMapper;
        this.flowService = flowService;
        this.userMapper = userMapper;
        this.deptService = deptService;
        this.stateMachine = stateMachine;
    }

    /** 数据范围参数，与 ReportService 口径保持一致 */
    private boolean allScope() {
        LoginUser current = UserContext.get();
        return isPureAuditor(current) ? false : current.isAllScope();
    }

    private String scopeUserId() {
        return isPureAuditor(UserContext.get()) ? "__none__" : UserContext.get().getUserId();
    }

    private List<String> scopeDeptIds() {
        LoginUser current = UserContext.get();
        if (isPureAuditor(current) || current.isAllScope() || !"DEPT".equals(current.getDataScope())) {
            return null;
        }
        return deptService.selfAndDescendantIds(current.getDeptId()).stream()
                .map(String::valueOf).collect(Collectors.toList());
    }

    private boolean isPureAuditor(LoginUser current) {
        return current.getRoles() != null && current.getRoles().size() == 1
                && current.hasRole(ROLE_AUDITOR);
    }

    public DashboardVO overview() {
        boolean all = allScope();
        String userId = scopeUserId();
        List<String> deptIds = scopeDeptIds();

        DashboardVO vo = new DashboardVO();

        // 按类型 + 状态分组，一次拿到流程条与在办数
        Map<String, Map<String, Integer>> stageCount = new HashMap<>();
        for (Map<String, Object> row : reportMapper.countByTypeAndStatus(all, userId, deptIds)) {
            String type = String.valueOf(row.get("caseType"));
            String status = String.valueOf(row.get("status"));
            int value = ((Number) row.get("value")).intValue();
            stageCount.computeIfAbsent(type, k -> new HashMap<>()).put(status, value);
        }

        for (Map.Entry<String, String> entry : FLOW_NAME.entrySet()) {
            String key = entry.getKey();
            Map<String, Integer> counts = stageCount.getOrDefault(key, Collections.emptyMap());
            DashboardVO.FlowVO flow = buildFlow(key, entry.getValue(), counts);
            vo.getFlows().add(flow);
            int active = flow.getStages().stream()
                    .filter(s -> !CaseStateMachine.CLOSED.equals(s.getCode())
                            && !CaseStateMachine.ARCHIVED.equals(s.getCode()))
                    .mapToInt(DashboardVO.StageVO::getCount).sum();
            flow.setTotal(active);
            vo.setActiveCount(vo.getActiveCount() + active);
            if (CaseStateMachine.TYPE_INJURY.equals(key)) {
                vo.setInjuryActiveCount(active);
            } else {
                vo.setArbitrationActiveCount(active);
            }
        }

        // 期限预警
        Map<String, Object> deadline = reportMapper.deadlineStat(all, userId, deptIds);
        if (deadline != null) {
            int total = number(deadline.get("total"));
            int done = number(deadline.get("done"));
            vo.setOverdueCount(number(deadline.get("overdue")));
            vo.setWarningCount(Math.max(0, total - done));
        }

        Integer closed = reportMapper.countClosedThisYear(all, userId, deptIds);
        vo.setYearClosedCount(closed == null ? 0 : closed);

        Double avgDays = reportMapper.avgCloseDays(all, userId, deptIds);
        vo.setAvgCloseDays(avgDays == null ? 0 : Math.round(avgDays));

        BigDecimal injuryAmount = reportMapper.sumInjuryAmount(all, userId, deptIds);
        BigDecimal arbAmount = reportMapper.sumAwardAmount(all, userId, deptIds);
        vo.setInjuryAmount(injuryAmount == null ? BigDecimal.ZERO : injuryAmount);
        vo.setArbitrationAmount(arbAmount == null ? BigDecimal.ZERO : arbAmount);
        vo.setTotalAmount(vo.getInjuryAmount().add(vo.getArbitrationAmount()));

        vo.setTodos(buildTodos(all, userId, deptIds));
        return vo;
    }

    /** 流程条：节点顺序与时限取自 wf_node_def，未配置的节点回退到状态机主链 */
    private DashboardVO.FlowVO buildFlow(String flowKey, String flowName, Map<String, Integer> counts) {
        DashboardVO.FlowVO flow = new DashboardVO.FlowVO();
        flow.setKey(flowKey);
        flow.setName(flowName);

        // 复用节点缓存：一次概览请求原本要回表 2 次（每个流程各一次）
        List<WfNodeDef> nodes = flowService.enabledNodes(flowKey);

        if (nodes.isEmpty()) {
            for (String code : stateMachine.mainChain(flowKey)) {
                DashboardVO.StageVO stage = new DashboardVO.StageVO();
                stage.setCode(code);
                stage.setName(CaseDict.statusName(code));
                stage.setCount(counts.getOrDefault(code, 0));
                stage.setNext(stateMachine.nextStatuses(flowKey, code));
                flow.getStages().add(stage);
            }
            return flow;
        }

        int mediation = counts.getOrDefault("MEDIATION", 0);
        for (WfNodeDef node : nodes) {
            DashboardVO.StageVO stage = new DashboardVO.StageVO();
            stage.setCode(node.getStatusCode());
            stage.setName(node.getNodeName());
            stage.setTimeLimit(node.getTimeLimit());
            int count = counts.getOrDefault(node.getStatusCode(), 0);
            // 调解结案与裁决同属「裁决/调解」节点，数量合并展示
            if ("AWARD".equals(node.getStatusCode())) {
                count += mediation;
            }
            stage.setCount(count);
            // 后继优先取配置，未配置回落内置拓扑
            List<String> next = WorkflowFlowService.parseCodes(node.getNextCodes());
            stage.setNext(next.isEmpty() ? stateMachine.nextStatuses(flowKey, node.getStatusCode()) : next);
            flow.getStages().add(stage);
        }
        return flow;
    }

    private List<DashboardVO.TodoVO> buildTodos(boolean all, String userId, List<String> deptIds) {
        List<Map<String, Object>> rows = reportMapper.urgentDeadlines(all, userId, deptIds, 6);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> handlerIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Object handler = row.get("handlerId");
            if (handler != null) {
                handlerIds.add(String.valueOf(handler));
            }
        }
        Map<String, String> handlerNames = handlerIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId, handlerIds))
                .stream().collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName, (a, b) -> a));

        LocalDate today = LocalDate.now();
        List<DashboardVO.TodoVO> todos = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            DashboardVO.TodoVO todo = new DashboardVO.TodoVO();
            todo.setCaseId(((Number) row.get("caseId")).longValue());
            todo.setCaseNo(String.valueOf(row.get("caseNo")));
            todo.setEmployeeName(maskName(String.valueOf(row.get("employeeName"))));
            todo.setStageName(String.valueOf(row.get("nodeName")));
            String handlerId = row.get("handlerId") == null ? null : String.valueOf(row.get("handlerId"));
            todo.setHandlerName(handlerNames.getOrDefault(handlerId, handlerId));
            Object deadlineDate = row.get("deadlineDate");
            if (deadlineDate != null) {
                LocalDate date = LocalDate.parse(String.valueOf(deadlineDate).substring(0, 10));
                todo.setDeadlineDate(date.format(DATE_FMT));
                long gap = ChronoUnit.DAYS.between(today, date);
                todo.setOverdue(gap < 0);
                todo.setDays(Math.abs(gap));
            }
            todos.add(todo);
        }
        return todos;
    }

    private int number(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private String maskName(String name) {
        if (name == null) {
            return null;
        }
        return allScope() ? name : CryptoUtil.maskName(name);
    }
}
