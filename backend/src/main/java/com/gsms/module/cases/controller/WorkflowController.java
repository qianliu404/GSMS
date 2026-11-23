package com.gsms.module.cases.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gsms.common.PageQuery;
import com.gsms.common.PageResult;
import com.gsms.common.R;
import com.gsms.module.cases.dto.NodeDefRequest;
import com.gsms.module.cases.entity.CaseMain;
import com.gsms.module.cases.entity.WfInstance;
import com.gsms.module.cases.entity.WfNodeDef;
import com.gsms.module.cases.mapper.CaseMainMapper;
import com.gsms.module.cases.mapper.WfInstanceMapper;
import com.gsms.module.cases.service.CaseDict;
import com.gsms.module.cases.service.CaseStateMachine;
import com.gsms.module.cases.service.WorkflowFlowService;
import com.gsms.module.cases.service.WorkflowNodeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程管理。当前为轻量状态机实现：对外暴露流程定义与运行实例，
 * 接口形态与 Flowable 对齐（定义/实例），后续替换引擎时前端无需改动。
 */
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowController {

    private final CaseStateMachine stateMachine;
    private final WfInstanceMapper wfInstanceMapper;
    private final CaseMainMapper caseMainMapper;
    private final WorkflowNodeService nodeService;
    private final WorkflowFlowService flowService;

    public WorkflowController(CaseStateMachine stateMachine, WfInstanceMapper wfInstanceMapper,
                              CaseMainMapper caseMainMapper, WorkflowNodeService nodeService,
                              WorkflowFlowService flowService) {
        this.stateMachine = stateMachine;
        this.wfInstanceMapper = wfInstanceMapper;
        this.caseMainMapper = caseMainMapper;
        this.nodeService = nodeService;
        this.flowService = flowService;
    }

    // ==================== 节点配置 ====================

    @GetMapping("/nodes")
    @PreAuthorize("hasAuthority('workflow:view')")
    public R<List<WfNodeDef>> nodes(@RequestParam(required = false) String flowKey) {
        return R.ok(nodeService.list(flowKey));
    }

    @PostMapping("/nodes")
    @PreAuthorize("hasAuthority('workflow:edit')")
    public R<Long> createNode(@Valid @RequestBody NodeDefRequest request) {
        return R.ok(nodeService.create(request));
    }

    @PutMapping("/nodes/{id}")
    @PreAuthorize("hasAuthority('workflow:edit')")
    public R<Void> updateNode(@PathVariable Long id, @Valid @RequestBody NodeDefRequest request) {
        nodeService.update(id, request);
        return R.ok();
    }

    /** direction: -1 上移，1 下移 */
    @PostMapping("/nodes/{id}/move")
    @PreAuthorize("hasAuthority('workflow:edit')")
    public R<Void> moveNode(@PathVariable Long id, @RequestParam int direction) {
        nodeService.move(id, direction);
        return R.ok();
    }

    @DeleteMapping("/nodes/{id}")
    @PreAuthorize("hasAuthority('workflow:delete')")
    public R<Void> deleteNode(@PathVariable Long id) {
        nodeService.delete(id);
        return R.ok();
    }

    /** 启用 / 停用节点：停用后不再作为流转目标，有在办案件时拒绝 */
    @PutMapping("/nodes/{id}/enabled")
    @PreAuthorize("hasAuthority('workflow:edit')")
    public R<Void> setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        nodeService.setEnabled(id, enabled);
        return R.ok();
    }

    @GetMapping("/definitions")
    @PreAuthorize("hasAuthority('workflow:view')")
    public R<List<Map<String, Object>>> definitions() {
        List<Map<String, Object>> result = new ArrayList<>();
        String[] types = {CaseStateMachine.TYPE_INJURY, CaseStateMachine.TYPE_ARBITRATION,
                CaseStateMachine.TYPE_RELATED};
        for (String type : types) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("processDefId", "case-" + type.toLowerCase());
            item.put("processName", CaseDict.typeName(type) + "办理流程");
            item.put("caseType", type);
            item.put("version", 1);
            item.put("status", "ACTIVE");
            item.put("engine", "内置状态机");

            List<String> chain = flowService.flowStatuses(type);
            item.put("chain", chain);
            item.put("chainNames", chain.stream().map(CaseDict::statusName).collect(Collectors.toList()));

            List<Map<String, Object>> nodes = new ArrayList<>();
            for (String status : chain) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("code", status);
                node.put("name", flowService.statusLabel(type, status));
                List<String> next = flowService.nextStatuses(type, status);
                node.put("next", next);
                node.put("nextNames", next.stream().map(CaseDict::statusName).collect(Collectors.toList()));
                node.put("canRollback", flowService.canRollback(type, status));
                node.put("terminal", next.isEmpty());
                nodes.add(node);
            }
            item.put("nodes", nodes);
            result.add(item);
        }
        return R.ok(result);
    }

    @GetMapping("/instances")
    @PreAuthorize("hasAuthority('workflow:view')")
    public R<PageResult<Map<String, Object>>> instances(@RequestParam(required = false) String status,
                                                         @RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        PageQuery query = new PageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);

        LambdaQueryWrapper<WfInstance> wrapper = new LambdaQueryWrapper<WfInstance>()
                .eq(status != null && !status.isEmpty(), WfInstance::getStatus, status)
                .orderByDesc(WfInstance::getId);
        Page<WfInstance> page = wfInstanceMapper.selectPage(query.toPage(), wrapper);

        List<Long> caseIds = page.getRecords().stream().map(WfInstance::getCaseId).collect(Collectors.toList());
        Map<Long, CaseMain> caseMap = caseIds.isEmpty() ? Collections.<Long, CaseMain>emptyMap()
                : caseMainMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(CaseMain::getId, c -> c, (a, b) -> a));

        List<Map<String, Object>> list = new ArrayList<>();
        for (WfInstance instance : page.getRecords()) {
            CaseMain caseMain = caseMap.get(instance.getCaseId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", instance.getId());
            item.put("caseId", instance.getCaseId());
            item.put("processDefId", instance.getProcessDefId());
            item.put("processInstanceId", instance.getProcessInstanceId());
            item.put("businessKey", instance.getBusinessKey());
            item.put("status", instance.getStatus());
            item.put("startTime", instance.getStartTime());
            item.put("endTime", instance.getEndTime());
            if (caseMain != null) {
                item.put("caseNo", caseMain.getCaseNo());
                item.put("caseName", caseMain.getCaseName());
                item.put("caseStatus", caseMain.getStatus());
                item.put("caseStatusName", CaseDict.statusName(caseMain.getStatus()));
            }
            list.add(item);
        }
        return R.ok(PageResult.of(page, list));
    }
}
