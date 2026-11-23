package com.gsms.module.cases.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.common.LoginUser;
import com.gsms.common.UserContext;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.cases.dto.NodeDefRequest;
import com.gsms.module.cases.entity.CaseMain;
import com.gsms.module.cases.entity.WfNodeDef;
import com.gsms.module.cases.mapper.CaseMainMapper;
import com.gsms.module.cases.mapper.WfNodeDefMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 流程节点配置。
 *
 * 边界：状态码仍由 CaseStateMachine 约束，节点表只决定「怎么展示、什么时限、谁办、要什么材料」，
 * 因此调序 / 改时限不会破坏状态机，也不会让历史流转记录失真。
 * 首末节点（草稿、归档）固定，不可删除、不可移动、不可停用，避免案件失去起始或终止状态。
 *
 * 拓扑维护：新增节点自动串入前驱与原后继之间，删除节点由前驱接续其后继，
 * 保证配置完成后流程图不断链、新节点可直接参与流转。
 */
@Service
public class WorkflowNodeService {

    /** 状态码格式：大写字母开头，可含大写字母/数字/下划线，最长 30 */
    private static final java.util.regex.Pattern STATUS_CODE_PATTERN =
            java.util.regex.Pattern.compile("^[A-Z][A-Z0-9_]{0,29}$");

    private final WfNodeDefMapper nodeDefMapper;
    private final CaseMainMapper caseMainMapper;
    private final CaseStateMachine stateMachine;
    private final WorkflowFlowService flowService;
    private final AuditService auditService;

    public WorkflowNodeService(WfNodeDefMapper nodeDefMapper, CaseMainMapper caseMainMapper,
                               CaseStateMachine stateMachine, WorkflowFlowService flowService,
                               AuditService auditService) {
        this.nodeDefMapper = nodeDefMapper;
        this.caseMainMapper = caseMainMapper;
        this.stateMachine = stateMachine;
        this.flowService = flowService;
        this.auditService = auditService;
    }

    /** 启动时把节点名称注册到状态字典，保证自定义状态在列表/详情里显示中文名 */
    @javax.annotation.PostConstruct
    public void init() {
        refreshStatusNames();
    }

    /** 重建状态名映射：以节点表为准，覆盖内置字典 */
    private void refreshStatusNames() {
        CaseDict.clearCustomStatus();
        for (WfNodeDef node : nodeDefMapper.selectList(null)) {
            CaseDict.registerStatus(node.getStatusCode(), node.getNodeName());
        }
    }

    public List<WfNodeDef> list(String flowKey) {
        return nodeDefMapper.selectList(new LambdaQueryWrapper<WfNodeDef>()
                .eq(StringUtils.hasText(flowKey), WfNodeDef::getFlowKey, flowKey)
                .orderByAsc(WfNodeDef::getFlowKey)
                .orderByAsc(WfNodeDef::getSortNo));
    }

    /** 节点定义影响所有在办案件的办理要素，仅管理员可改 */
    private void assertAdmin() {
        LoginUser current = UserContext.get();
        if (!current.hasRole("ROLE_SYS_ADMIN") && !current.hasRole("ROLE_CASE_ADMIN")) {
            throw new BizException(ErrorCode.FORBIDDEN, "流程节点配置需系统管理员或案件管理员权限");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(NodeDefRequest req) {
        assertAdmin();
        if (!StringUtils.hasText(req.getNodeName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "节点名称不能为空");
        }
        // 状态码不再要求人工填写：未传时按节点名称自动生成
        String statusCode = StringUtils.hasText(req.getStatusCode())
                ? req.getStatusCode().trim().toUpperCase()
                : generateStatusCode(req.getNodeName());
        // 支持自定义状态：只校验编码格式，不再限制必须在内置列表里
        if (!STATUS_CODE_PATTERN.matcher(statusCode).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "状态码只能由大写字母、数字、下划线组成，且以字母开头（如 ARBITRATION_REVIEW）");
        }
        Long exists = nodeDefMapper.selectCount(new LambdaQueryWrapper<WfNodeDef>()
                .eq(WfNodeDef::getFlowKey, req.getFlowKey())
                .eq(WfNodeDef::getStatusCode, statusCode));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该状态码已存在节点，请直接编辑");
        }

        List<WfNodeDef> nodes = list(req.getFlowKey());
        if (nodes.size() < 2) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程节点不足，无法插入");
        }
        // 新节点只能插在草稿之后、归档之前，避免落到归档之后导致流程无终点
        int position = req.getInsertAfter() == null ? nodes.size() - 1 : req.getInsertAfter();
        position = Math.max(1, Math.min(position, nodes.size() - 1));
        WfNodeDef previous = nodes.get(position - 1);
        if (stateMachine.ARCHIVED.equals(previous.getStatusCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能插入到归档节点之后");
        }
        // 新节点继承前驱原有的后继（已过滤停用），前驱改为指向新节点，保证拓扑连续
        List<String> inherited = new ArrayList<>(
                flowService.nextStatuses(req.getFlowKey(), previous.getStatusCode()));

        WfNodeDef entity = new WfNodeDef();
        entity.setFlowKey(req.getFlowKey());
        entity.setStatusCode(statusCode);
        entity.setNodeName(req.getNodeName());
        entity.setTimeLimit(req.getTimeLimit());
        entity.setMainRole(req.getMainRole());
        entity.setKeyMaterial(req.getKeyMaterial());
        entity.setSysAction(req.getSysAction());
        entity.setAllowRollback(Boolean.FALSE.equals(req.getAllowRollback()) ? 0 : 1);
        // 用户显式配置的后继优先，否则继承前驱原后继
        entity.setNextCodes(StringUtils.hasText(req.getNextCodes())
                ? normalizeNextCodes(req.getFlowKey(), statusCode, req.getNextCodes())
                : (inherited.isEmpty() ? null : String.join(",", inherited)));
        entity.setEnabled(1);
        entity.setSortNo(position + 1);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        nodeDefMapper.insert(entity);
        resort(req.getFlowKey(), entity.getId(), position + 1);

        previous.setNextCodes(statusCode);
        previous.setUpdateTime(LocalDateTime.now());
        nodeDefMapper.updateById(previous);

        refreshStatusNames();
        flowService.evictCache();
        auditService.success("流程管理", "新增节点", String.valueOf(entity.getId()),
                req.getFlowKey() + "/" + statusCode + "/" + req.getNodeName());
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, NodeDefRequest req) {
        assertAdmin();
        WfNodeDef entity = require(id);
        // 首末节点的状态码不可改，否则状态机会失去入口/出口
        List<WfNodeDef> nodes = list(entity.getFlowKey());
        boolean edge = isFirst(nodes, entity) || isLast(nodes, entity);
        if (StringUtils.hasText(req.getStatusCode()) && !req.getStatusCode().equals(entity.getStatusCode()) && edge) {
            throw new BizException(ErrorCode.PARAM_INVALID, "首末节点的状态码不可修改");
        }

        String nextCodes = entity.getNextCodes();
        if (req.getNextCodes() != null) {
            nextCodes = normalizeNextCodes(entity.getFlowKey(), entity.getStatusCode(), req.getNextCodes());
            assertNoCycle(entity.getFlowKey(), entity.getStatusCode(), WorkflowFlowService.parseCodes(nextCodes));
        }
        entity.setNodeName(req.getNodeName());
        entity.setTimeLimit(req.getTimeLimit());
        entity.setMainRole(req.getMainRole());
        entity.setKeyMaterial(req.getKeyMaterial());
        entity.setSysAction(req.getSysAction());
        if (req.getAllowRollback() != null) {
            entity.setAllowRollback(req.getAllowRollback() ? 1 : 0);
        }
        entity.setNextCodes(nextCodes);
        entity.setUpdateTime(LocalDateTime.now());
        nodeDefMapper.updateById(entity);
        // updateById 会忽略 null 字段，清空后继需显式 set，否则旧的显式配置残留
        if (nextCodes == null) {
            clearNextCodes(id);
        }
        refreshStatusNames();
        flowService.evictCache();
        auditService.success("流程管理", "编辑节点", String.valueOf(id),
                entity.getFlowKey() + "/" + entity.getStatusCode() + "/" + entity.getNodeName());
    }

    /** 上移 / 下移一位，首末节点保持固定；调序只影响展示顺序，不改变后继关系 */
    @Transactional(rollbackFor = Exception.class)
    public void move(Long id, int direction) {
        assertAdmin();
        WfNodeDef entity = require(id);
        List<WfNodeDef> nodes = list(entity.getFlowKey());
        int index = indexOf(nodes, entity.getId());
        int target = index + direction;
        if (index < 0 || target < 0 || target >= nodes.size()) {
            return;
        }
        if (isFirst(nodes, entity) || isLast(nodes, entity)
                || isFirst(nodes, nodes.get(target)) || isLast(nodes, nodes.get(target))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "首节点与归档节点位置固定");
        }
        WfNodeDef other = nodes.get(target);
        Integer a = entity.getSortNo();
        entity.setSortNo(other.getSortNo());
        other.setSortNo(a);
        nodeDefMapper.updateById(entity);
        nodeDefMapper.updateById(other);
        flowService.evictCache();
        auditService.success("流程管理", "调整节点顺序", String.valueOf(id),
                entity.getFlowKey() + "/" + entity.getStatusCode() + "/" + (direction < 0 ? "上移" : "下移"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        assertAdmin();
        WfNodeDef entity = require(id);
        List<WfNodeDef> nodes = list(entity.getFlowKey());
        if (nodes.size() <= 3) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程至少保留 3 个节点");
        }
        if (isFirst(nodes, entity) || isLast(nodes, entity)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "草稿与归档节点为系统节点，不可删除");
        }
        Long occupied = caseMainMapper.selectCount(new LambdaQueryWrapper<CaseMain>()
                .eq(CaseMain::getCaseType, entity.getFlowKey())
                .eq(CaseMain::getStatus, entity.getStatusCode()));
        if (occupied != null && occupied > 0) {
            throw new BizException(ErrorCode.STATUS_CONFLICT,
                    "有 " + occupied + " 件案件停留在此节点，请先流转后再删除");
        }
        List<String> removedNext = new ArrayList<>(
                flowService.nextStatuses(entity.getFlowKey(), entity.getStatusCode()));
        if (removedNext.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该节点没有可用的后继节点，删除后流程会中断");
        }

        nodeDefMapper.deleteById(id);
        // 前驱接续被删节点的后继：显式指向它的改为指向其后继，内置拓扑指向它的补一条显式配置
        for (WfNodeDef other : list(entity.getFlowKey())) {
            if (other.getId().equals(id) || isArchived(other)) {
                continue;
            }
            List<String> next = new ArrayList<>(WorkflowFlowService.parseCodes(other.getNextCodes()));
            boolean explicitHit = next.remove(entity.getStatusCode());
            boolean builtInHit = !StringUtils.hasText(other.getNextCodes())
                    && stateMachine.nextStatuses(entity.getFlowKey(), other.getStatusCode())
                    .contains(entity.getStatusCode());
            if (!explicitHit && !builtInHit) {
                continue;
            }
            // 分支节点只摘除被删的那一条，不把其余分支一并改写
            if (!explicitHit || next.isEmpty()) {
                next.addAll(removedNext);
            }
            String stored = storedNext(entity.getFlowKey(), other.getStatusCode(), next);
            other.setNextCodes(stored);
            other.setUpdateTime(LocalDateTime.now());
            nodeDefMapper.updateById(other);
            if (stored == null) {
                clearNextCodes(other.getId());
            }
        }
        resort(entity.getFlowKey(), null, 0);
        refreshStatusNames();
        flowService.evictCache();
        auditService.success("流程管理", "删除节点", String.valueOf(id),
                entity.getFlowKey() + "/" + entity.getStatusCode() + "/" + entity.getNodeName());
    }

    /** 启用 / 停用节点：停用后不再作为流转目标，且不能有在办案件停留 */
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(Long id, boolean enabled) {
        assertAdmin();
        WfNodeDef entity = require(id);
        List<WfNodeDef> nodes = list(entity.getFlowKey());
        if (!enabled) {
            if (isFirst(nodes, entity) || isLast(nodes, entity)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "草稿与归档节点不可停用");
            }
            Long occupied = caseMainMapper.selectCount(new LambdaQueryWrapper<CaseMain>()
                    .eq(CaseMain::getCaseType, entity.getFlowKey())
                    .eq(CaseMain::getStatus, entity.getStatusCode()));
            if (occupied != null && occupied > 0) {
                throw new BizException(ErrorCode.STATUS_CONFLICT,
                        "有 " + occupied + " 件案件停留在此节点，请先流转后再停用");
            }
            assertOthersStillMovable(entity, nodes);
        }
        entity.setEnabled(enabled ? 1 : 0);
        entity.setUpdateTime(LocalDateTime.now());
        nodeDefMapper.updateById(entity);
        flowService.evictCache();
        auditService.success("流程管理", enabled ? "启用节点" : "停用节点", String.valueOf(id),
                entity.getFlowKey() + "/" + entity.getStatusCode() + "/" + entity.getNodeName());
    }

    /** 停用前确认没有前驱只剩这一条出路，否则流程会在该前驱处断掉 */
    private void assertOthersStillMovable(WfNodeDef target, List<WfNodeDef> nodes) {
        Set<String> blocked = new HashSet<>();
        for (WfNodeDef node : nodes) {
            if (node.getEnabled() != null && node.getEnabled() == 0) {
                blocked.add(node.getStatusCode());
            }
        }
        blocked.add(target.getStatusCode());
        for (WfNodeDef other : nodes) {
            if (other.getId().equals(target.getId()) || isArchived(other)
                    || (other.getEnabled() != null && other.getEnabled() == 0)) {
                continue;
            }
            List<String> next = new ArrayList<>(flowService.effectiveNext(other.getFlowKey(), other));
            if (!next.contains(target.getStatusCode())) {
                continue;
            }
            boolean hasAlternative = false;
            for (String code : next) {
                if (!blocked.contains(code)) {
                    hasAlternative = true;
                    break;
                }
            }
            if (!hasAlternative) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "停用后「" + other.getNodeName() + "」将没有可流转的下一节点，请先调整其后继");
            }
        }
    }

    /**
     * 保存后的后继值：与内置拓扑语义一致时置空，回落到内置解析，避免留下无意义的显式配置。
     */
    /** 显式清空后继：MyBatis-Plus 的 updateById 会跳过 null 字段 */
    private void clearNextCodes(Long id) {
        nodeDefMapper.update(null, new LambdaUpdateWrapper<WfNodeDef>()
                .eq(WfNodeDef::getId, id)
                .set(WfNodeDef::getNextCodes, null));
    }

    private String storedNext(String flowKey, String code, List<String> next) {
        List<String> builtIn = stateMachine.nextStatuses(flowKey, code);
        if (next.isEmpty()) {
            return null;
        }
        if (new HashSet<>(next).equals(new HashSet<>(builtIn))) {
            return null;
        }
        return String.join(",", next);
    }

    /** 环检测：从每个后继出发做 DFS，若能回到当前节点则说明配置成环 */
    private void assertNoCycle(String flowKey, String selfCode, List<String> nextCodes) {
        Deque<String> stack = new ArrayDeque<>(nextCodes);
        Set<String> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (current.equals(selfCode)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "后继节点形成环：会重新回到「" + stateMachineLabel(flowKey, selfCode) + "」");
            }
            if (!visited.add(current)) {
                continue;
            }
            WfNodeDef node = findNode(flowKey, current);
            List<String> next;
            if (node == null) {
                next = stateMachine.nextStatuses(flowKey, current);
            } else {
                next = new ArrayList<>(flowService.effectiveNext(flowKey, node));
            }
            stack.addAll(next);
        }
    }

    private WfNodeDef findNode(String flowKey, String statusCode) {
        for (WfNodeDef node : flowService.allNodes(flowKey)) {
            if (node.getStatusCode().equals(statusCode)) {
                return node;
            }
        }
        return null;
    }

    private String stateMachineLabel(String flowKey, String code) {
        WfNodeDef node = findNode(flowKey, code);
        return node == null ? CaseDict.statusName(code) : node.getNodeName();
    }

    /** 重排 sort_no：可把指定节点移到目标位，其余保持相对顺序 */
    private void resort(String flowKey, Long moveId, int targetSort) {
        List<WfNodeDef> nodes = list(flowKey);
        WfNodeDef moving = null;
        if (moveId != null) {
            for (WfNodeDef node : nodes) {
                if (node.getId().equals(moveId)) {
                    moving = node;
                    break;
                }
            }
            nodes.remove(moving);
            if (moving != null) {
                int idx = Math.max(0, Math.min(targetSort - 1, nodes.size()));
                nodes.add(idx, moving);
            }
        }
        int sort = 1;
        for (WfNodeDef node : nodes) {
            if (node.getSortNo() == null || node.getSortNo() != sort) {
                node.setSortNo(sort);
                nodeDefMapper.updateById(node);
            }
            sort++;
        }
    }

    /**
     * 按节点名称自动生成状态码。
     * 优先提取名称里的英文/数字（「Review 复核」→ REVIEW），纯中文时回落为 NODE_序号。
     * 状态名在 CaseDict 中是全局注册的，所以查重范围取全表，避免两个流程各自生成 NODE_01 互相覆盖。
     */
    private String generateStatusCode(String nodeName) {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (WfNodeDef node : nodeDefMapper.selectList(null)) {
            if (node.getStatusCode() != null) {
                used.add(node.getStatusCode());
            }
        }

        String base = slug(nodeName);
        if (base.isEmpty()) {
            int seq = used.size() + 1;
            String candidate;
            do {
                candidate = String.format("NODE_%02d", seq++);
            } while (used.contains(candidate));
            return candidate;
        }
        if (!used.contains(base)) {
            return base;
        }
        int i = 2;
        while (used.contains(base + "_" + i)) {
            i++;
        }
        // 冲突时追加序号，整体长度仍要控制在 30 以内
        return base.substring(0, Math.min(base.length(), 27)) + "_" + i;
    }

    /** 提取 ASCII 标识：字母转大写、数字保留，其余字符折叠为单个下划线；提取不到有效标识时返回空串 */
    private String slug(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                sb.append((char) (c - 32));
            } else if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                sb.append('_');
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
            sb.setLength(sb.length() - 1);
        }
        // 状态码必须以字母开头，提取结果以数字开头（如「2次复核」）同样视为无效，交给序号兜底
        if (sb.length() == 0 || Character.isDigit(sb.charAt(0))) {
            return "";
        }
        return sb.length() > 30 ? sb.substring(0, 30) : sb.toString();
    }

    /**
     * 后继节点校验：必须属于同一流程、不能指向自身、不能指向停用节点，去重后按逗号拼接；
     * 空串表示回落到内置拓扑。
     */
    private String normalizeNextCodes(String flowKey, String selfCode, String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        List<String> allowed = flowService.flowStatuses(flowKey);
        List<String> codes = new java.util.ArrayList<>();
        for (String code : raw.split(",")) {
            String trimmed = code.trim().toUpperCase();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.equals(selfCode)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "后继节点不能指向自身：" + trimmed);
            }
            if (!allowed.contains(trimmed)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "后继节点「" + trimmed + "」不属于该流程或已停用，可选：" + String.join("/", allowed));
            }
            if (!codes.contains(trimmed)) {
                codes.add(trimmed);
            }
        }
        return codes.isEmpty() ? null : String.join(",", codes);
    }

    private WfNodeDef require(Long id) {
        WfNodeDef entity = nodeDefMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "节点配置不存在");
        }
        return entity;
    }

    private int indexOf(List<WfNodeDef> nodes, Long id) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 首末节点按状态码认定（草稿 / 归档），不能按排序位置判断：
     * 新增节点默认追加到末尾，若按位置判断，新节点会立刻变成"末节点"从而既不能删也不能移。
     */
    private boolean isFirst(List<WfNodeDef> nodes, WfNodeDef node) {
        return CaseStateMachine.DRAFT.equals(node.getStatusCode());
    }

    private boolean isLast(List<WfNodeDef> nodes, WfNodeDef node) {
        return CaseStateMachine.ARCHIVED.equals(node.getStatusCode());
    }

    private boolean isArchived(WfNodeDef node) {
        return CaseStateMachine.ARCHIVED.equals(node.getStatusCode());
    }
}
