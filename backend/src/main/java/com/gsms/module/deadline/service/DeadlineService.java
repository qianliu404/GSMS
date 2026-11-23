package com.gsms.module.deadline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gsms.common.*;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.cases.entity.WfNodeDef;
import com.gsms.module.cases.dto.CaseDetailVO;
import com.gsms.module.cases.entity.CaseMain;
import com.gsms.module.cases.mapper.CaseMainMapper;
import com.gsms.module.cases.mapper.WfNodeDefMapper;
import com.gsms.module.cases.service.CaseDict;
import com.gsms.module.cases.service.CaseStateMachine;
import com.gsms.module.collab.service.NotificationService;
import com.gsms.module.deadline.dto.DeadlineQuery;
import com.gsms.module.deadline.dto.DeadlineVO;
import com.gsms.module.deadline.entity.DeadlineReminder;
import com.gsms.module.deadline.mapper.DeadlineReminderMapper;
import com.gsms.module.system.entity.SysUser;
import com.gsms.module.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 期限中心。
 *
 * 截止日期按「流程节点配置」的时限推算：进入节点当天 + 时限天数（工作日，跳过周末），
 * 截止日前 7 个自然日进入提醒，距截止日不足 7 天显示「临近超期」。
 * 历史案件仍保留原有的法定时限记录（如 INJURY_APPLY），流转时按别名一并关闭。
 */
@Slf4j
@Service
public class DeadlineService {

    /** 统一提醒窗口：截止日前 7 个自然日进入提醒 */
    private static final int REMIND_DAYS = 7;

    /** 临近超期阈值：距截止日不足 7 天 */
    public static final long DUE_SOON_DAYS = 7;

    /** 法定时限节点（其余按内部时限登记），仅用于期限中心的类型区分 */
    private static final Set<String> LEGAL_NODES = new HashSet<>(Arrays.asList(
            "DECLARE", "RECOGNITION", "APPRAISAL",
            "FILED", "HEARING", "AWARD"));

    /**
     * 流程状态 → 内置法定时限节点编码。
     *
     * 法定时限编码（如 INJURY_APPLY、ARB_HEARING）与流程状态码（如 DECLARE、HEARING）是两套体系，
     * 同名的 FILED 在工伤与仲裁里含义也不同，因此按案件类型分开映射。
     * 流转离开某状态时按此映射一并关闭对应期限，否则已办完的节点会一直挂着「超期」。
     */
    private static final Map<String, Map<String, String>> STATUS_NODE_ALIAS = new HashMap<>();

    static {
        Map<String, String> injury = new HashMap<>();
        injury.put("DECLARE", "INJURY_APPLY");
        injury.put("RECOGNITION", "INJURY_RECOGNIZE");
        injury.put("APPRAISAL", "INJURY_APPRAISAL");
        injury.put("TREATMENT", "INJURY_PAYMENT");
        STATUS_NODE_ALIAS.put(CaseStateMachine.TYPE_INJURY, injury);

        Map<String, String> arbitration = new HashMap<>();
        arbitration.put("FILED", "ARB_ACCEPT");
        arbitration.put("HEARING", "ARB_HEARING");
        arbitration.put("AWARD", "ARB_AWARD");
        STATUS_NODE_ALIAS.put(CaseStateMachine.TYPE_ARBITRATION, arbitration);
    }

    private final DeadlineReminderMapper deadlineMapper;
    private final CaseMainMapper caseMainMapper;
    private final WfNodeDefMapper nodeDefMapper;
    private final SysUserMapper userMapper;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public DeadlineService(DeadlineReminderMapper deadlineMapper, CaseMainMapper caseMainMapper,
                           WfNodeDefMapper nodeDefMapper, SysUserMapper userMapper,
                           NotificationService notificationService, AuditService auditService) {
        this.deadlineMapper = deadlineMapper;
        this.caseMainMapper = caseMainMapper;
        this.nodeDefMapper = nodeDefMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /** 建档后按案件当前所处节点生成期限节点 */
    @Transactional(rollbackFor = Exception.class)
    public void initForCase(CaseMain caseMain) {
        if (caseMain == null) {
            return;
        }
        enterNode(caseMain, caseMain.getStatus());
    }

    /**
     * 进入流程节点：按节点时限（工作日）生成/刷新该节点的期限记录。
     *
     * 时限取「流程节点配置」里的 time_limit（如「10 天」），「视情况」「—」不建记录。
     * 起算日取本节点「办理日期」：进入节点时默认当天，用户可在案件详情修改；历史数据
     * 未记录办理日期时仍按案件发生日期推算。法定节假日不计入，调休上班的周末计入。
     * 同一案件同一节点只保留一条：退回重办时按新的办理日期重算并清掉人工调整值。
     */
    @Transactional(rollbackFor = Exception.class)
    public void enterNode(CaseMain caseMain, String statusCode) {
        if (caseMain == null || caseMain.getId() == null || !StringUtils.hasText(statusCode)) {
            return;
        }
        WfNodeDef node = nodeDefMapper.selectOne(new LambdaQueryWrapper<WfNodeDef>()
                .eq(WfNodeDef::getFlowKey, caseMain.getCaseType())
                .eq(WfNodeDef::getStatusCode, statusCode)
                .last("LIMIT 1"));
        if (node == null) {
            return;
        }
        Integer days = WorkDayUtils.parseDays(node.getTimeLimit());
        if (days == null) {
            return;
        }
        LocalDate handleDate = LocalDate.now();
        LocalDate dueDate = WorkDayUtils.plusWorkingDays(handleDate, days);
        String deadlineType = LEGAL_NODES.contains(statusCode) ? "LEGAL" : "INTERNAL";

        DeadlineReminder existing = deadlineMapper.selectOne(new LambdaQueryWrapper<DeadlineReminder>()
                .eq(DeadlineReminder::getCaseId, caseMain.getId())
                .eq(DeadlineReminder::getNodeCode, statusCode)
                .orderByDesc(DeadlineReminder::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setNodeName(node.getNodeName());
            existing.setDeadlineType(deadlineType);
            existing.setDeadlineDate(dueDate);
            existing.setManualDate(null);
            existing.setHandleDate(handleDate);
            // 重新进入节点（含退回重办）视为未开启，需再次「提交」才开始计时
            existing.setStartTime(null);
            existing.setRemindDays(REMIND_DAYS);
            existing.setStatus("PENDING");
            existing.setUpdateTime(LocalDateTime.now());
            deadlineMapper.updateById(existing);
            return;
        }

        DeadlineReminder entity = new DeadlineReminder();
        entity.setCaseId(caseMain.getId());
        entity.setNodeCode(statusCode);
        entity.setNodeName(node.getNodeName());
        entity.setDeadlineType(deadlineType);
        entity.setDeadlineDate(dueDate);
        entity.setHandleDate(handleDate);
        entity.setRemindDays(REMIND_DAYS);
        entity.setStatus("PENDING");
        entity.setHandlerId(caseMain.getHandlerId());
        entity.setOverdueLevel(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        deadlineMapper.insert(entity);
    }

    public PageResult<DeadlineVO> page(DeadlineQuery query) {
        LambdaQueryWrapper<DeadlineReminder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getCaseId() != null, DeadlineReminder::getCaseId, query.getCaseId())
                .eq(StringUtils.hasText(query.getStatus()), DeadlineReminder::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getDeadlineType()), DeadlineReminder::getDeadlineType, query.getDeadlineType())
                .le(query.getDateTo() != null, DeadlineReminder::getDeadlineDate, query.getDateTo())
                .ge(query.getDateFrom() != null, DeadlineReminder::getDeadlineDate, query.getDateFrom())
                .orderByAsc(DeadlineReminder::getDeadlineDate);

        if (Boolean.TRUE.equals(query.getOverdue())) {
            wrapper.eq(DeadlineReminder::getStatus, "OVERDUE");
        }
        // 数据权限：非全局范围只看自己名下或自己参与案件的节点
        LoginUser current = UserContext.get();
        if (!current.isAllScope()) {
            List<Long> caseIds = caseMainMapper.selectMemberCaseIds(current.getUserId());
            if (caseIds.isEmpty()) {
                wrapper.eq(DeadlineReminder::getId, -1L);
            } else {
                wrapper.in(DeadlineReminder::getCaseId, caseIds);
            }
        }

        Page<DeadlineReminder> page = deadlineMapper.selectPage(query.toPage(), wrapper);
        if (page.getRecords().isEmpty()) {
            return PageResult.of(page, Collections.<DeadlineVO>emptyList());
        }
        Set<Long> caseIds = page.getRecords().stream()
                .map(DeadlineReminder::getCaseId).collect(Collectors.toSet());
        Map<Long, CaseMain> caseMap = caseMainMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(CaseMain::getId, c -> c, (a, b) -> a));
        Map<String, String> userNames = loadUserNames(page.getRecords());

        List<DeadlineVO> list = new ArrayList<>();
        for (DeadlineReminder entity : page.getRecords()) {
            list.add(toVO(entity, caseMap.get(entity.getCaseId()), userNames));
        }
        return PageResult.of(page, list);
    }

    private DeadlineVO toVO(DeadlineReminder entity, CaseMain caseMain, Map<String, String> userNames) {
        DeadlineVO vo = new DeadlineVO();
        vo.setId(entity.getId());
        vo.setCaseId(entity.getCaseId());
        vo.setNodeCode(entity.getNodeCode());
        vo.setNodeName(entity.getNodeName());
        vo.setDeadlineType(entity.getDeadlineType());
        vo.setDeadlineTypeName("LEGAL".equals(entity.getDeadlineType()) ? "法定时限" : "内部时限");
        vo.setDeadlineDate(entity.getDeadlineDate());
        vo.setRemindDays(entity.getRemindDays());
        vo.setStatus(entity.getStatus());
        boolean started = entity.getStartTime() != null;
        vo.setStatusName(started ? statusName(entity.getStatus())
                : ("DONE".equals(entity.getStatus()) ? "已完成" : "未开启"));
        vo.setHandlerId(entity.getHandlerId());
        vo.setHandlerName(userNames.get(entity.getHandlerId()));
        vo.setOverdueLevel(entity.getOverdueLevel());
        vo.setOverdueReason(entity.getOverdueReason());
        vo.setSuperviseOpinion(entity.getSuperviseOpinion());
        if (caseMain != null) {
            vo.setCaseNo(caseMain.getCaseNo());
            vo.setCaseName(caseMain.getCaseName());
            vo.setCaseType(caseMain.getCaseType());
        }
        // 未开启的节点只显示截止日期，不参与超期/临近超期判定
        if (started && entity.getDeadlineDate() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), entity.getDeadlineDate());
            vo.setRemainDays(days);
            if (days < 0 && !"DONE".equals(entity.getStatus())) {
                vo.setOverdue(true);
                vo.setOverdueDays(-days);
            } else if (!"DONE".equals(entity.getStatus()) && days < DUE_SOON_DAYS) {
                // 距截止日不足 7 个自然日：状态按临近超期展示，DB 状态仍是待提醒/已提醒
                vo.setDueSoon(true);
                vo.setStatusName("临近超期");
            }
        }
        return vo;
    }

    private Map<String, String> loadUserNames(List<DeadlineReminder> records) {
        Set<String> ids = records.stream().map(DeadlineReminder::getHandlerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId, ids)).stream()
                .collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName, (a, b) -> a));
    }

    private String statusName(String status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case "PENDING":
                return "待提醒";
            case "REMINDED":
                return "已提醒";
            case "OVERDUE":
                return "已超期";
            case "DONE":
                return "已完成";
            default:
                return status;
        }
    }

    /**
     * 每日 08:00 扫描：进入提醒窗口的置为 REMINDED，已过截止日的置为 OVERDUE 并升级预警。
     * 关闭 IM/邮件时仅落站内信，核心业务不受影响。
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void scheduledScan() {
        int[] result = scan();
        log.info("期限扫描完成: 提醒 {} 条, 超期 {} 条", result[0], result[1]);
    }

    @Transactional(rollbackFor = Exception.class)
    public int[] scan() {
        LocalDate today = LocalDate.now();

        // 只查真正需要变更的节点：原来一次捞全部 PENDING/REMINDED，数据量上来后是长事务+内存峰值
        // 只扫描案件仍停留在该节点的期限记录，避免已办完的历史节点继续被判超期
        List<DeadlineReminder> overdueList = deadlineMapper.selectOverdueCandidates(today);
        List<DeadlineReminder> remindList = deadlineMapper.selectRemindCandidates(today);

        // 集合式更新：一条 SQL 完成状态流转，取代逐行 updateById
        int overdue = overdueList.isEmpty() ? 0 : deadlineMapper.markOverdue(today);
        int reminded = remindList.isEmpty() ? 0 : deadlineMapper.markReminded(today);

        for (DeadlineReminder item : overdueList) {
            notificationService.sendInner(item.getHandlerId(), "期限已超期",
                    String.format("案件期限节点【%s】已于 %s 超期，请立即处理",
                            item.getNodeName(), item.getDeadlineDate()),
                    "DEADLINE_OVERDUE", String.valueOf(item.getId()));
        }
        for (DeadlineReminder item : remindList) {
            // SQL 内已按各行 remind_days 精确更新，这里同步复核，保证通知与落库状态一致
            int remindDays = item.getRemindDays() == null ? 3 : item.getRemindDays();
            if (item.getDeadlineDate().isAfter(today.plusDays(remindDays))) {
                continue;
            }
            notificationService.sendInner(item.getHandlerId(), "期限即将到期",
                    String.format("案件期限节点【%s】将于 %s 到期，请提前准备",
                            item.getNodeName(), item.getDeadlineDate()),
                    "DEADLINE_REMIND", String.valueOf(item.getId()));
        }
        return new int[]{reminded, overdue};
    }

    /** 案件流转离开某节点：把该节点的期限置为已完成，超期标记随之消失 */
    @Transactional(rollbackFor = Exception.class)
    public int completeByStatus(Long caseId, String nodeCode) {
        if (caseId == null || !StringUtils.hasText(nodeCode)) {
            return 0;
        }
        // 手工/历史遗留的期限用状态码直接登记，先按原值关闭
        int affected = deadlineMapper.markDoneByNode(caseId, nodeCode);
        // 内置法定时限用另一套编码，需按案件类型映射后一并关闭
        String alias = aliasOf(caseId, nodeCode);
        if (alias != null && !alias.equals(nodeCode)) {
            affected += deadlineMapper.markDoneByNode(caseId, alias);
        }
        return affected;
    }

    /** 结案/归档：剩余未完成节点一并关闭 */
    @Transactional(rollbackFor = Exception.class)
    public int completeAll(Long caseId) {
        return caseId == null ? 0 : deadlineMapper.markAllDone(caseId);
    }

    /**
     * 退回某节点：把该节点已关闭的期限重新打开。
     * 退回意味着该节点需要重新办理，若不重开，节点会一直处于「已完成」状态而不再提醒。
     */
    @Transactional(rollbackFor = Exception.class)
    public int reopenByStatus(Long caseId, String nodeCode) {
        if (caseId == null || !StringUtils.hasText(nodeCode)) {
            return 0;
        }
        List<String> codes = new ArrayList<>();
        codes.add(nodeCode);
        String alias = aliasOf(caseId, nodeCode);
        if (alias != null && !alias.equals(nodeCode)) {
            codes.add(alias);
        }
        int affected = 0;
        for (String code : codes) {
            affected += deadlineMapper.reopenByNode(caseId, code, LocalDate.now());
        }
        return affected;
    }

    /** 当前案件还剩多少超期未处理的期限节点，用于结案时留痕 */
    public int countOverdue(Long caseId) {
        if (caseId == null) {
            return 0;
        }
        Long count = deadlineMapper.selectCount(new LambdaQueryWrapper<DeadlineReminder>()
                .eq(DeadlineReminder::getCaseId, caseId)
                .eq(DeadlineReminder::getStatus, "OVERDUE"));
        return count == null ? 0 : count.intValue();
    }

    private String aliasOf(Long caseId, String status) {
        CaseMain caseMain = caseMainMapper.selectById(caseId);
        if (caseMain == null || caseMain.getCaseType() == null) {
            return null;
        }
        Map<String, String> mapping = STATUS_NODE_ALIAS.get(caseMain.getCaseType());
        return mapping == null ? null : mapping.get(status);
    }

    /** 供控制器做访问权校验 */
    public DeadlineReminder get(Long id) {
        return deadlineMapper.selectById(id);
    }

    /**
     * 时限起算日：统一取案件「发生日期」。
     *
     * 立案时登记的发生日期是整条时间轴的起点，未填时退回建档时间，避免出现空起算日。
     */
    public static LocalDate baseDate(CaseMain caseMain) {
        if (caseMain == null) {
            return LocalDate.now();
        }
        if (caseMain.getOccurDate() != null) {
            return caseMain.getOccurDate();
        }
        return caseMain.getCreateTime() == null ? LocalDate.now() : caseMain.getCreateTime().toLocalDate();
    }

    /**
     * 人工调整当前节点截止日期。
     *
     * 与自动推算值一致时清空 manual_date 回到自动口径；不一致则落 manual_date，
     * 后续详情页、期限中心、提醒扫描都以人工值为准。
     */
    public CaseDetailVO.NodeDeadlineVO adjustDeadline(Long caseId, String nodeCode, LocalDate deadlineDate, String reason) {
        return adjustDeadline(caseId, nodeCode, deadlineDate, reason, null);
    }

    /**
     * 调整办理日期或截止日期。
     * 传入 handleDate 时按「办理日期 + 节点时限（工作日，含节假日）」重算截止日期并清除人工调整；
     * 仅传 deadlineDate 时维持原有人工调整口径。
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseDetailVO.NodeDeadlineVO adjustDeadline(Long caseId, String nodeCode, LocalDate deadlineDate,
                                                       String reason, LocalDate handleDate) {
        if (deadlineDate == null && handleDate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择办理日期或截止日期");
        }
        CaseMain caseMain = caseMainMapper.selectById(caseId);
        if (caseMain == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "案件不存在");
        }
        String code = StringUtils.hasText(nodeCode) ? nodeCode : caseMain.getStatus();
        WfNodeDef node = nodeDefMapper.selectOne(new LambdaQueryWrapper<WfNodeDef>()
                .eq(WfNodeDef::getFlowKey, caseMain.getCaseType())
                .eq(WfNodeDef::getStatusCode, code)
                .last("LIMIT 1"));
        String nodeName = node != null ? node.getNodeName() : CaseDict.statusName(code);
        Integer days = node == null ? null : WorkDayUtils.parseDays(node.getTimeLimit());
        LocalDate base = handleDate != null ? handleDate : baseDate(caseMain);
        LocalDate autoDate = days == null ? null : WorkDayUtils.plusWorkingDays(base, days);
        if (handleDate != null && autoDate != null) {
            deadlineDate = autoDate;
        }

        DeadlineReminder entity = deadlineMapper.selectOne(new LambdaQueryWrapper<DeadlineReminder>()
                .eq(DeadlineReminder::getCaseId, caseId)
                .eq(DeadlineReminder::getNodeCode, code)
                .orderByDesc(DeadlineReminder::getId)
                .last("LIMIT 1"));
        boolean insert = entity == null;
        if (insert) {
            entity = new DeadlineReminder();
            entity.setCaseId(caseId);
            entity.setNodeCode(code);
            entity.setHandlerId(caseMain.getHandlerId());
            entity.setOverdueLevel(0);
            entity.setCreateTime(LocalDateTime.now());
        }
        entity.setNodeName(nodeName);
        entity.setDeadlineType(LEGAL_NODES.contains(code) ? "LEGAL" : "INTERNAL");
        entity.setDeadlineDate(deadlineDate);
        if (handleDate != null) {
            // 改办理日期后截止日期完全按规则重算，不再保留旧的手动调整
            entity.setHandleDate(handleDate);
            entity.setManualDate(null);
        } else {
            // 与自动值相同就不算人工调整，退回重办时按规则重算即可
            entity.setManualDate(autoDate != null && autoDate.equals(deadlineDate) ? null : deadlineDate);
        }
        entity.setRemindDays(REMIND_DAYS);
        // 未开启的节点改期只影响截止日期，不提前判超期
        if (!"DONE".equals(entity.getStatus())) {
            entity.setStatus(entity.getStartTime() != null && deadlineDate.isBefore(LocalDate.now())
                    ? "OVERDUE" : "PENDING");
        }
        entity.setUpdateTime(LocalDateTime.now());
        if (insert) {
            deadlineMapper.insert(entity);
        } else {
            deadlineMapper.updateById(entity);
        }
        String auditText = handleDate != null
                ? String.format("%s 办理日期调整为 %s，截止日期重算为 %s", nodeName, handleDate, deadlineDate)
                : String.format("%s 截止日期调整为 %s", nodeName, deadlineDate);
        if (StringUtils.hasText(reason)) {
            auditText += "（" + reason + "）";
        }
        auditService.success("案件详情", handleDate != null ? "调整办理日期" : "调整截止日期",
                String.valueOf(caseId), auditText);

        return buildNodeVO(entity, base, autoDate);
    }

    /**
     * 开启当前节点计时：承办人点击「提交」后，该节点才开始按截止日期判断超期。
     * 重复开启不改变首次开启时间，保持计时连续。
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseDetailVO.NodeDeadlineVO openNode(Long caseId, String nodeCode) {
        CaseMain caseMain = caseMainMapper.selectById(caseId);
        if (caseMain == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "案件不存在");
        }
        String code = StringUtils.hasText(nodeCode) ? nodeCode : caseMain.getStatus();
        DeadlineReminder entity = findLatest(caseId, code);
        if (entity == null) {
            // 旧数据没有期限记录时按节点时限补建，再开启
            enterNode(caseMain, code);
            entity = findLatest(caseId, code);
        }
        if (entity == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该节点未配置时限，无需开启计时");
        }
        if (entity.getStartTime() == null) {
            entity.setStartTime(LocalDateTime.now());
            if (!"DONE".equals(entity.getStatus())) {
                entity.setStatus(entity.getDeadlineDate() != null && entity.getDeadlineDate().isBefore(LocalDate.now())
                        ? "OVERDUE" : "PENDING");
            }
            entity.setUpdateTime(LocalDateTime.now());
            deadlineMapper.updateById(entity);
            auditService.success("案件详情", "开启节点计时", String.valueOf(caseId), entity.getNodeName());
        }
        Integer days = nodeTimeLimit(caseMain.getCaseType(), code);
        LocalDate base = baseDate(caseMain);
        LocalDate autoDate = days == null ? null : WorkDayUtils.plusWorkingDays(base, days);
        return buildNodeVO(entity, base, autoDate);
    }

    /** 各节点开启计时的时间（nodeCode → startTime），未开启的不返回 */
    public Map<String, LocalDateTime> startedAt(Long caseId) {
        if (caseId == null) {
            return Collections.emptyMap();
        }
        List<DeadlineReminder> list = deadlineMapper.selectList(new LambdaQueryWrapper<DeadlineReminder>()
                .eq(DeadlineReminder::getCaseId, caseId)
                .isNotNull(DeadlineReminder::getStartTime));
        Map<String, LocalDateTime> map = new HashMap<>();
        for (DeadlineReminder item : list) {
            map.put(item.getNodeCode(), item.getStartTime());
        }
        return map;
    }

    private DeadlineReminder findLatest(Long caseId, String nodeCode) {
        return deadlineMapper.selectOne(new LambdaQueryWrapper<DeadlineReminder>()
                .eq(DeadlineReminder::getCaseId, caseId)
                .eq(DeadlineReminder::getNodeCode, nodeCode)
                .orderByDesc(DeadlineReminder::getId)
                .last("LIMIT 1"));
    }

    private Integer nodeTimeLimit(String caseType, String statusCode) {
        WfNodeDef node = nodeDefMapper.selectOne(new LambdaQueryWrapper<WfNodeDef>()
                .eq(WfNodeDef::getFlowKey, caseType)
                .eq(WfNodeDef::getStatusCode, statusCode)
                .last("LIMIT 1"));
        return node == null ? null : WorkDayUtils.parseDays(node.getTimeLimit());
    }

    /** 节点期限展示对象：统一出口，避免各处口径不一致 */
    private CaseDetailVO.NodeDeadlineVO buildNodeVO(DeadlineReminder entity, LocalDate base, LocalDate autoDate) {
        CaseDetailVO.NodeDeadlineVO vo = new CaseDetailVO.NodeDeadlineVO();
        vo.setStatusCode(entity.getNodeCode());
        vo.setNodeName(entity.getNodeName());
        vo.setEnterDate(base);
        vo.setHandleDate(entity.getHandleDate());
        vo.setAutoDate(autoDate);
        vo.setDeadlineDate(entity.getDeadlineDate());
        vo.setManual(entity.getManualDate() != null);
        vo.setStarted(entity.getStartTime() != null);
        vo.setStartTime(entity.getStartTime());
        if ("DONE".equals(entity.getStatus())) {
            vo.setState("DONE");
            return vo;
        }
        if (entity.getStartTime() == null) {
            // 未开启：只给截止日期，不计剩余天数与超期
            vo.setState("NOT_STARTED");
            return vo;
        }
        long remain = entity.getDeadlineDate() == null ? 0
                : ChronoUnit.DAYS.between(LocalDate.now(), entity.getDeadlineDate());
        vo.setRemainDays(remain);
        if (remain < 0) {
            vo.setState("OVERDUE");
        } else if (remain < DUE_SOON_DAYS) {
            vo.setState("DUE_SOON");
        } else {
            vo.setState("NORMAL");
        }
        return vo;
    }

    /** 各节点人工调整过的截止日期（nodeCode → date），供案件详情拼装时间轴 */
    public Map<String, LocalDate> manualDates(Long caseId) {
        if (caseId == null) {
            return Collections.emptyMap();
        }
        List<DeadlineReminder> list = deadlineMapper.selectList(new LambdaQueryWrapper<DeadlineReminder>()
                .eq(DeadlineReminder::getCaseId, caseId)
                .isNotNull(DeadlineReminder::getManualDate));
        Map<String, LocalDate> map = new HashMap<>();
        for (DeadlineReminder item : list) {
            map.put(item.getNodeCode(), item.getManualDate());
        }
        return map;
    }

    /** 各节点办理日期（nodeCode → handleDate），用于详情页按办理日期推算截止日期 */
    public Map<String, LocalDate> handleDates(Long caseId) {
        if (caseId == null) {
            return Collections.emptyMap();
        }
        List<DeadlineReminder> list = deadlineMapper.selectList(new LambdaQueryWrapper<DeadlineReminder>()
                .eq(DeadlineReminder::getCaseId, caseId)
                .isNotNull(DeadlineReminder::getHandleDate));
        Map<String, LocalDate> map = new HashMap<>();
        for (DeadlineReminder item : list) {
            map.put(item.getNodeCode(), item.getHandleDate());
        }
        return map;
    }

    @Transactional(rollbackFor = Exception.class)
    public void supervise(Long id, String opinion) {
        DeadlineReminder entity = require(id);
        LoginUser current = UserContext.get();
        if (!current.hasRole("ROLE_DEPT_HEAD") && !current.hasRole("ROLE_CASE_ADMIN")
                && !current.hasRole("ROLE_SYS_ADMIN")) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅部门负责人或管理员可督办");
        }
        entity.setSuperviseOpinion(opinion);
        entity.setUpdateTime(LocalDateTime.now());
        deadlineMapper.updateById(entity);
        notificationService.sendInner(entity.getHandlerId(), "期限督办",
                "督办意见：" + opinion, "DEADLINE_SUPERVISE", String.valueOf(id));
        auditService.success("期限中心", "督办", String.valueOf(id), opinion);
    }

    @Transactional(rollbackFor = Exception.class)
    public void registerReason(Long id, String reason) {
        DeadlineReminder entity = require(id);
        entity.setOverdueReason(reason);
        entity.setUpdateTime(LocalDateTime.now());
        deadlineMapper.updateById(entity);
        auditService.success("期限中心", "登记超期原因", String.valueOf(id), reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markDone(Long id) {
        DeadlineReminder entity = require(id);
        entity.setStatus("DONE");
        entity.setUpdateTime(LocalDateTime.now());
        deadlineMapper.updateById(entity);
        auditService.success("期限中心", "节点完成", String.valueOf(id), entity.getNodeName());
    }

    private DeadlineReminder require(Long id) {
        DeadlineReminder entity = deadlineMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "期限节点不存在");
        }
        return entity;
    }
}
