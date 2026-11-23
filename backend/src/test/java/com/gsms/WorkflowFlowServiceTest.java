package com.gsms;

import com.gsms.module.cases.entity.WfNodeDef;
import com.gsms.module.cases.mapper.WfNodeDefMapper;
import com.gsms.module.cases.service.CaseStateMachine;
import com.gsms.module.cases.service.WorkflowFlowService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 分支流转解析：配置优先、配置缺失回落内置、分支节点可反查前驱。
 */
class WorkflowFlowServiceTest {

    private final WfNodeDefMapper mapper = mock(WfNodeDefMapper.class);
    private final WorkflowFlowService service =
            new WorkflowFlowService(mapper, new CaseStateMachine());

    private WfNodeDef node(String code, String nextCodes, int allowRollback) {
        WfNodeDef node = new WfNodeDef();
        node.setFlowKey(CaseStateMachine.TYPE_ARBITRATION);
        node.setStatusCode(code);
        node.setNextCodes(nextCodes);
        node.setAllowRollback(allowRollback);
        node.setEnabled(1);
        return node;
    }

    @Test
    void configuredBranchShouldOverrideBuiltIn() {
        when(mapper.selectList(any())).thenReturn(Arrays.asList(
                node("AWARD", "PERFORM,APPEAL", 1),
                node("APPEAL", "RETRIAL_HEARING", 1)));

        List<String> next = service.nextStatuses(CaseStateMachine.TYPE_ARBITRATION, "AWARD");
        assertEquals(Arrays.asList("PERFORM", "APPEAL"), next);
        assertTrue(service.canTransit(CaseStateMachine.TYPE_ARBITRATION, "AWARD", "APPEAL"));
        assertTrue(service.canTransit(CaseStateMachine.TYPE_ARBITRATION, "AWARD", "PERFORM"));
    }

    @Test
    void shouldFallBackToBuiltInWhenNotConfigured() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());

        assertEquals(Arrays.asList("PERFORM", CaseStateMachine.APPEAL),
                service.nextStatuses(CaseStateMachine.TYPE_ARBITRATION, "AWARD"));
        assertEquals(Collections.singletonList(CaseStateMachine.RULING),
                service.nextStatuses(CaseStateMachine.TYPE_ARBITRATION, CaseStateMachine.RETRIAL_HEARING));
    }

    @Test
    void branchNodeShouldRollbackToItsPredecessor() {
        when(mapper.selectList(any())).thenReturn(Arrays.asList(
                node("AWARD", "PERFORM,APPEAL", 1),
                node("APPEAL", "RETRIAL_HEARING", 1)));

        assertEquals("AWARD", service.previousStatus(CaseStateMachine.TYPE_ARBITRATION, "APPEAL"));
        assertTrue(service.canRollback(CaseStateMachine.TYPE_ARBITRATION, "APPEAL"));
        assertFalse(service.canRollback(CaseStateMachine.TYPE_ARBITRATION, CaseStateMachine.ARCHIVED));
    }

    @Test
    void rollbackDisabledOnNodeShouldBeRespected() {
        when(mapper.selectList(any())).thenReturn(
                Collections.singletonList(node("RETRIAL_HEARING", "RULING", 0)));

        assertFalse(service.canRollback(CaseStateMachine.TYPE_ARBITRATION, "RETRIAL_HEARING"));
    }

    /** 立案可直接跳到认定：可选推进目标覆盖全部下游，且排除结案/归档 */
    @Test
    void forwardTargetsShouldAllowSkippingIntermediateNodes() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());

        List<String> targets = service.forwardTargets(CaseStateMachine.TYPE_INJURY, CaseStateMachine.FILED);
        assertEquals("INVESTIGATION", targets.get(0));
        assertTrue(targets.contains("RECOGNITION"));
        assertTrue(targets.contains("DISPUTE"));
        assertFalse(targets.contains(CaseStateMachine.CLOSED));
        assertFalse(targets.contains(CaseStateMachine.ARCHIVED));
    }

    /** 退回可选更早节点：待遇处理可退回立案，但不含草稿 */
    @Test
    void rollbackTargetsShouldIncludeEarlierNodes() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());

        List<String> targets = service.rollbackTargets(CaseStateMachine.TYPE_INJURY, "TREATMENT");
        assertEquals("APPRAISAL", targets.get(0));
        assertTrue(targets.contains(CaseStateMachine.FILED));
        assertFalse(targets.contains(CaseStateMachine.DRAFT));
    }

    /** 节点关闭「允许退回」后，可选退回目标为空 */
    @Test
    void rollbackTargetsShouldBeEmptyWhenNodeDisallows() {
        when(mapper.selectList(any())).thenReturn(
                Collections.singletonList(node("RETRIAL_HEARING", "RULING", 0)));

        assertTrue(service.rollbackTargets(CaseStateMachine.TYPE_ARBITRATION, "RETRIAL_HEARING").isEmpty());
    }
}
