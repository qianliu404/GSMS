package com.gsms;

import com.gsms.common.BizException;
import com.gsms.common.LoginUser;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.cases.dto.NodeDefRequest;
import com.gsms.module.cases.entity.WfNodeDef;
import com.gsms.module.cases.mapper.CaseMainMapper;
import com.gsms.module.cases.mapper.WfNodeDefMapper;
import com.gsms.module.cases.service.CaseStateMachine;
import com.gsms.module.cases.service.WorkflowFlowService;
import com.gsms.module.cases.service.WorkflowNodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 节点配置的拓扑维护：新增接链、删除重连、成环拒绝、停用保护。
 */
class WorkflowNodeServiceTest {

    private final WfNodeDefMapper mapper = mock(WfNodeDefMapper.class);
    private final CaseMainMapper caseMainMapper = mock(CaseMainMapper.class);
    private final WorkflowFlowService flowService = mock(WorkflowFlowService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final CaseStateMachine stateMachine = new CaseStateMachine();
    private final List<WfNodeDef> store = new ArrayList<>();

    private WorkflowNodeService service;
    private long seq = 100;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser(1L, "admin", "管理员", 1L,
                Collections.singletonList("ROLE_SYS_ADMIN"), Collections.emptyList(), "ALL");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));

        when(mapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(store));
        when(mapper.selectById(any())).thenAnswer(inv -> find(((Long) inv.getArgument(0))));
        when(mapper.insert(any(WfNodeDef.class))).thenAnswer(inv -> {
            WfNodeDef node = inv.getArgument(0);
            node.setId(++seq);
            store.add(node);
            return 1;
        });
        when(mapper.updateById(any(WfNodeDef.class))).thenAnswer(inv -> {
            WfNodeDef node = inv.getArgument(0);
            WfNodeDef old = find(node.getId());
            if (old != null) {
                old.setNodeName(node.getNodeName());
                old.setNextCodes(node.getNextCodes());
                old.setSortNo(node.getSortNo());
                old.setEnabled(node.getEnabled());
            }
            return 1;
        });
        when(mapper.deleteById(any(java.io.Serializable.class))).thenAnswer(inv -> {
            store.removeIf(node -> node.getId().equals(inv.getArgument(0)));
            return 1;
        });
        when(caseMainMapper.selectCount(any())).thenReturn(0L);
        when(flowService.allNodes(anyString())).thenAnswer(inv -> new ArrayList<>(store));
        when(flowService.effectiveNext(anyString(), any())).thenAnswer(inv -> nextOf(inv.getArgument(1)));
        when(flowService.nextStatuses(anyString(), anyString()))
                .thenAnswer(inv -> nextOf(findByCode(inv.getArgument(1))));
        when(flowService.flowStatuses(anyString())).thenAnswer(inv -> {
            List<String> codes = new ArrayList<>();
            for (WfNodeDef node : store) {
                if (!codes.contains(node.getStatusCode())) {
                    codes.add(node.getStatusCode());
                }
            }
            return codes;
        });
        doAnswer(inv -> null).when(flowService).evictCache();

        service = new WorkflowNodeService(mapper, caseMainMapper, stateMachine, flowService, auditService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private WfNodeDef node(String code, String next, int enabled) {
        WfNodeDef node = new WfNodeDef();
        node.setId(++seq);
        node.setFlowKey(CaseStateMachine.TYPE_INJURY);
        node.setStatusCode(code);
        node.setNodeName(code);
        node.setSortNo(store.size() + 1);
        node.setNextCodes(next);
        node.setEnabled(enabled);
        return node;
    }

    private WfNodeDef find(Long id) {
        for (WfNodeDef node : store) {
            if (node.getId().equals(id)) {
                return node;
            }
        }
        return null;
    }

    private WfNodeDef findByCode(String code) {
        for (WfNodeDef node : store) {
            if (node.getStatusCode().equals(code)) {
                return node;
            }
        }
        return null;
    }

    private List<String> nextOf(WfNodeDef node) {
        if (node == null) {
            return Collections.emptyList();
        }
        return WorkflowFlowService.parseCodes(node.getNextCodes()).isEmpty()
                ? stateMachine.nextStatuses(node.getFlowKey(), node.getStatusCode())
                : WorkflowFlowService.parseCodes(node.getNextCodes());
    }

    @Test
    void createShouldLinkPredecessorAndInheritItsNext() {
        store.add(node("DRAFT", "FILED", 1));
        store.add(node("FILED", "CLOSED", 1));
        store.add(node("CLOSED", "ARCHIVED", 1));
        store.add(node("ARCHIVED", null, 1));

        NodeDefRequest req = new NodeDefRequest();
        req.setFlowKey(CaseStateMachine.TYPE_INJURY);
        req.setStatusCode("REVIEW");
        req.setNodeName("伤情复核");
        req.setInsertAfter(1);

        Long id = service.create(req);

        WfNodeDef created = find(id);
        assertEquals("FILED", created.getNextCodes(), "新节点应继承前驱原有后继");
        assertEquals("REVIEW", findByCode("DRAFT").getNextCodes(), "前驱应改为指向新节点");
        assertEquals(2, created.getSortNo(), "新节点应插在前驱之后");
    }

    @Test
    void deleteShouldReconnectPredecessor() {
        store.add(node("DRAFT", "MID", 1));
        store.add(node("MID", "CLOSED", 1));
        store.add(node("CLOSED", "ARCHIVED", 1));
        store.add(node("ARCHIVED", null, 1));
        Long mid = findByCode("MID").getId();

        service.delete(mid);

        assertEquals(null, findByCode("MID"), "节点应被删除");
        assertEquals("CLOSED", findByCode("DRAFT").getNextCodes(), "前驱应接续被删节点的后继");
    }

    @Test
    void deleteShouldRefuseTerminalNode() {
        store.add(node("DRAFT", "MID", 1));
        store.add(node("MID", null, 1));
        store.add(node("CLOSED", "ARCHIVED", 1));
        store.add(node("ARCHIVED", null, 1));

        assertThrows(BizException.class, () -> service.delete(findByCode("MID").getId()),
                "无后继的节点删除会让流程中断，应拒绝");
    }

    @Test
    void updateShouldRejectCycle() {
        store.add(node("A", "B", 1));
        store.add(node("B", "A", 1));

        NodeDefRequest req = new NodeDefRequest();
        req.setFlowKey(CaseStateMachine.TYPE_INJURY);
        req.setNodeName("B");
        req.setNextCodes("A");

        assertThrows(BizException.class, () -> service.update(findByCode("B").getId(), req),
                "后继回到自身形成环，应拒绝");
    }

    @Test
    void disableShouldBeRejectedWhenCasesInProgress() {
        store.add(node("DRAFT", "MID", 1));
        store.add(node("MID", "CLOSED", 1));
        store.add(node("CLOSED", "ARCHIVED", 1));
        store.add(node("ARCHIVED", null, 1));
        when(caseMainMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.setEnabled(findByCode("MID").getId(), false),
                "有在办案件停留时不可停用");
    }

    @Test
    void disableShouldBeRejectedWhenPredecessorLosesOnlyExit() {
        store.add(node("DRAFT", "MID", 1));
        store.add(node("MID", "CLOSED", 1));
        store.add(node("CLOSED", "ARCHIVED", 1));
        store.add(node("ARCHIVED", null, 1));

        BizException error = assertThrows(BizException.class,
                () -> service.setEnabled(findByCode("MID").getId(), false));
        assertTrue(error.getMessage().contains("没有可流转的下一节点"), error.getMessage());
    }

    @Test
    void enableShouldBeAllowedWithoutCaseCheck() {
        store.add(node("DRAFT", "MID", 1));
        store.add(node("MID", "CLOSED", 0));
        store.add(node("CLOSED", "ARCHIVED", 1));
        store.add(node("ARCHIVED", null, 1));

        service.setEnabled(findByCode("MID").getId(), true);

        assertEquals(1, findByCode("MID").getEnabled());
    }

    @Test
    void createShouldClampInsertPositionBeforeArchived() {
        store.add(node("DRAFT", "FILED", 1));
        store.add(node("FILED", "CLOSED", 1));
        store.add(node("CLOSED", "ARCHIVED", 1));
        store.add(node("ARCHIVED", null, 1));

        NodeDefRequest req = new NodeDefRequest();
        req.setFlowKey(CaseStateMachine.TYPE_INJURY);
        req.setStatusCode("TAIL");
        req.setNodeName("尾部节点");
        req.setInsertAfter(4);

        // insertAfter 被夹到归档之前，不会追加到归档之后
        Long id = service.create(req);
        assertEquals("ARCHIVED", find(id).getNextCodes(), "新节点应指向归档");
        assertEquals("TAIL", findByCode("CLOSED").getNextCodes(), "前驱应为归档前一个节点");
    }
}
