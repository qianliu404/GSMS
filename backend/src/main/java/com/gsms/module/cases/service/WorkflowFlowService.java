package com.gsms.module.cases.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsms.module.cases.entity.WfNodeDef;
import com.gsms.module.cases.mapper.WfNodeDefMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程流转解析。
 *
 * 优先级：wf_node_def.next_codes（节点配置页可编辑的分支） > CaseStateMachine 内置拓扑。
 * 这样既允许业务按需增删分支，「流程节点配置」里没配后继时也不会让老流程失效。
 */
@Service
public class WorkflowFlowService {

    private final WfNodeDefMapper nodeDefMapper;
    private final CaseStateMachine stateMachine;

    /** 全量节点（含停用）：列表每行都要算 progress，避免重复回表 */
    private final Map<String, List<WfNodeDef>> allCache = new ConcurrentHashMap<>();
    /** 启用节点：参与流转与步骤条 */
    private final Map<String, List<WfNodeDef>> enabledCache = new ConcurrentHashMap<>();
    /** 停用状态码：配置了也不允许再流转过去 */
    private final Map<String, Set<String>> disabledCache = new ConcurrentHashMap<>();

    public WorkflowFlowService(WfNodeDefMapper nodeDefMapper, CaseStateMachine stateMachine) {
        this.nodeDefMapper = nodeDefMapper;
        this.stateMachine = stateMachine;
    }

    public List<WfNodeDef> allNodes(String flowKey) {
        if (!StringUtils.hasText(flowKey)) {
            return Collections.emptyList();
        }
        return allCache.computeIfAbsent(flowKey, key -> Collections.unmodifiableList(
                nodeDefMapper.selectList(new LambdaQueryWrapper<WfNodeDef>()
                        .eq(WfNodeDef::getFlowKey, key)
                        .orderByAsc(WfNodeDef::getSortNo))));
    }

    public List<WfNodeDef> enabledNodes(String flowKey) {
        if (!StringUtils.hasText(flowKey)) {
            return Collections.emptyList();
        }
        return enabledCache.computeIfAbsent(flowKey, key -> {
            List<WfNodeDef> enabled = new ArrayList<>();
            for (WfNodeDef node : allNodes(key)) {
                if (node.getEnabled() == null || node.getEnabled() == 1) {
                    enabled.add(node);
                }
            }
            return Collections.unmodifiableList(enabled);
        });
    }

    /** 节点配置（新增/修改/排序/删除/启停）变更后调用，避免继续返回旧拓扑 */
    public void evictCache() {
        allCache.clear();
        enabledCache.clear();
        disabledCache.clear();
    }

    /** 当前状态允许推进到的下一状态：本节点配了后继就用配置，否则沿用内置拓扑；停用节点不参与流转 */
    public List<String> nextStatuses(String flowKey, String current) {
        List<String> raw = configuredOrDefault(flowKey, current);
        Set<String> disabled = disabledCodes(flowKey);
        if (disabled.isEmpty() || raw.isEmpty()) {
            return raw;
        }
        List<String> valid = new ArrayList<>(raw.size());
        for (String code : raw) {
            if (!disabled.contains(code)) {
                valid.add(code);
            }
        }
        return valid;
    }

    public boolean canTransit(String flowKey, String from, String to) {
        return nextStatuses(flowKey, from).contains(to);
    }

    /** 退回目标：主链优先；分支节点按配置的后继反查前驱；停用节点不可作为退回目标 */
    public String previousStatus(String flowKey, String current) {
        Set<String> disabled = disabledCodes(flowKey);
        String previous = stateMachine.previousStatus(flowKey, current);
        if (previous != null && !disabled.contains(previous)) {
            return previous;
        }
        List<WfNodeDef> nodes = allNodes(flowKey);
        if (hasConfiguredEdges(nodes)) {
            for (WfNodeDef node : nodes) {
                if (disabled.contains(node.getStatusCode())) {
                    continue;
                }
                if (parseCodes(node.getNextCodes()).contains(current)) {
                    return node.getStatusCode();
                }
            }
        }
        // 兜底：按流程内全部状态反查（含自定义状态）
        for (String status : flowStatuses(flowKey)) {
            if (stateMachine.nextStatuses(flowKey, status).contains(current)) {
                return status;
            }
        }
        return null;
    }

    public boolean canRollback(String flowKey, String current) {
        if (CaseStateMachine.ARCHIVED.equals(current)) {
            return false;
        }
        String previous = previousStatus(flowKey, current);
        if (previous == null) {
            return false;
        }
        for (WfNodeDef node : allNodes(flowKey)) {
            if (node.getStatusCode().equals(current)) {
                // 节点显式配置为禁止退回时以配置为准
                return node.getAllowRollback() == null || node.getAllowRollback() == 1;
            }
        }
        return true;
    }

    /**
     * 可自定义推进的目标：当前节点的全部下游节点（沿后继链可达），支持跳过中间节点。
     * 按广度优先返回，距离近的在前；结案/归档各有专门动作，不作为流转目标。
     */
    public List<String> forwardTargets(String flowKey, String current) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(current)) {
            return result;
        }
        Set<String> visited = new HashSet<>();
        visited.add(current);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(current);
        while (!queue.isEmpty()) {
            for (String next : nextStatuses(flowKey, queue.poll())) {
                if (!visited.add(next)) {
                    continue;
                }
                queue.add(next);
                if (!CaseStateMachine.CLOSED.equals(next)
                        && !CaseStateMachine.ARCHIVED.equals(next)
                        && !CaseStateMachine.DRAFT.equals(next)
                        && !result.contains(next)) {
                    result.add(next);
                }
            }
        }
        return result;
    }

    /** 全部上游节点（反向可达），广度优先返回，最近的前驱在前 */
    public List<String> predecessors(String flowKey, String current) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(current)) {
            return result;
        }
        Set<String> visited = new HashSet<>();
        visited.add(current);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(current);
        while (!queue.isEmpty()) {
            String to = queue.poll();
            for (String from : flowStatuses(flowKey)) {
                if (visited.contains(from) || !nextStatuses(flowKey, from).contains(to)) {
                    continue;
                }
                visited.add(from);
                queue.add(from);
                result.add(from);
            }
        }
        return result;
    }

    /** 可选退回目标：上游节点，最近的前驱在前；节点关闭「允许退回」时为空 */
    public List<String> rollbackTargets(String flowKey, String current) {
        List<String> result = new ArrayList<>();
        if (!canRollback(flowKey, current)) {
            return result;
        }
        for (String code : predecessors(flowKey, current)) {
            // 退回草稿等于重开案件，结案/归档有专门动作，均不作为退回目标
            if (!CaseStateMachine.DRAFT.equals(code)
                    && !CaseStateMachine.CLOSED.equals(code)
                    && !CaseStateMachine.ARCHIVED.equals(code)) {
                result.add(code);
            }
        }
        return result;
    }

    /** 流程内全部状态码：启用节点在前，内置拓扑补充未建节点/未停用的状态 */
    public List<String> flowStatuses(String flowKey) {
        List<String> codes = new ArrayList<>();
        for (WfNodeDef node : enabledNodes(flowKey)) {
            if (!codes.contains(node.getStatusCode())) {
                codes.add(node.getStatusCode());
            }
        }
        Set<String> disabled = disabledCodes(flowKey);
        for (String code : stateMachine.allStatuses(flowKey)) {
            if (!codes.contains(code) && !disabled.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    /** 进度百分比：按启用节点顺序计算；案件停留在已停用节点时按全量顺序兜底，避免显示 0% */
    public int progress(String flowKey, String status) {
        List<String> chain = flowStatuses(flowKey);
        int index = chain.indexOf(status);
        int size = chain.size();
        if (index < 0) {
            List<String> all = new ArrayList<>();
            for (WfNodeDef node : allNodes(flowKey)) {
                if (!all.contains(node.getStatusCode())) {
                    all.add(node.getStatusCode());
                }
            }
            for (String code : stateMachine.allStatuses(flowKey)) {
                if (!all.contains(code)) {
                    all.add(code);
                }
            }
            index = all.indexOf(status);
            size = all.size();
        }
        if (index < 0 || size == 0) {
            return 0;
        }
        return (int) Math.round((index + 1) * 100.0 / size);
    }

    /** 状态中文名：节点名优先（自定义状态据此展示），未配置回落内置字典 */
    public String statusLabel(String flowKey, String code) {
        for (WfNodeDef node : allNodes(flowKey)) {
            if (node.getStatusCode().equals(code)) {
                return node.getNodeName();
            }
        }
        return CaseDict.statusName(code);
    }

    /** 节点有效后继：显式配置优先，为空回落内置拓扑（不过滤停用，由调用方决定） */
    public List<String> effectiveNext(String flowKey, WfNodeDef node) {
        List<String> configured = parseCodes(node.getNextCodes());
        return configured.isEmpty() ? stateMachine.nextStatuses(flowKey, node.getStatusCode()) : configured;
    }

    private List<String> configuredOrDefault(String flowKey, String current) {
        for (WfNodeDef node : allNodes(flowKey)) {
            if (node.getStatusCode().equals(current)) {
                return effectiveNext(flowKey, node);
            }
        }
        return stateMachine.nextStatuses(flowKey, current);
    }

    private Set<String> disabledCodes(String flowKey) {
        if (!StringUtils.hasText(flowKey)) {
            return Collections.emptySet();
        }
        return disabledCache.computeIfAbsent(flowKey, key -> {
            Set<String> codes = new HashSet<>();
            for (WfNodeDef node : allNodes(key)) {
                if (node.getEnabled() != null && node.getEnabled() == 0) {
                    codes.add(node.getStatusCode());
                }
            }
            return Collections.unmodifiableSet(codes);
        });
    }

    private boolean hasConfiguredEdges(List<WfNodeDef> nodes) {
        for (WfNodeDef node : nodes) {
            if (StringUtils.hasText(node.getNextCodes())) {
                return true;
            }
        }
        return false;
    }

    /** "PERFORM,APPEAL" -> [PERFORM, APPEAL] */
    public static List<String> parseCodes(String codes) {
        if (!StringUtils.hasText(codes)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String code : codes.split(",")) {
            String trimmed = code.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
