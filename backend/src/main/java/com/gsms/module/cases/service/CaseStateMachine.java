package com.gsms.module.cases.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 案件状态机（轻量实现，替代 Flowable）。
 *
 * 工伤：草稿→立案→调查取证→申报→认定→鉴定→待遇处理→争议处理→结案→归档
 * 仲裁：草稿→立案→证据准备→开庭→裁决/调解→履行→结案→归档
 *       裁决后按当事人态度分支：服从→履行→结案；不服→上诉→开庭→裁定→履行→结案
 *       （裁决与裁定都只是「确认结果」，实际履行完毕才能结案）
 * 关联：草稿→立案→结案→归档
 *
 * 规则来源：开发文档 12.1 / 12.2。待遇处理可直接结案（不经争议处理）。
 * 分支节点可在「流程节点配置」中通过 wf_node_def.next_codes 覆盖，本类提供默认拓扑与兜底。
 */
@Component
public class CaseStateMachine {

    public static final String TYPE_INJURY = "INJURY";
    public static final String TYPE_ARBITRATION = "ARBITRATION";
    public static final String TYPE_RELATED = "RELATED";

    public static final String DRAFT = "DRAFT";
    public static final String FILED = "FILED";
    public static final String CLOSED = "CLOSED";
    public static final String ARCHIVED = "ARCHIVED";
    /** 仲裁裁决后的上诉分支 */
    public static final String APPEAL = "APPEAL";
    public static final String RETRIAL_HEARING = "RETRIAL_HEARING";
    public static final String RULING = "RULING";

    /** 主链：用于计算退回目标（分支节点不在主链上，退回由 WorkflowFlowService 反查前驱） */
    private static final Map<String, List<String>> MAIN_CHAIN = new LinkedHashMap<>();
    /** 展示顺序：含分支节点，用于进度与步骤条排序 */
    private static final Map<String, List<String>> DISPLAY_ORDER = new LinkedHashMap<>();
    /** 邻接表：from -> 允许的 to 集合 */
    private static final Map<String, Map<String, List<String>>> FLOW = new LinkedHashMap<>();

    static {
        // ---------- 工伤 ----------
        MAIN_CHAIN.put(TYPE_INJURY, Collections.unmodifiableList(Arrays.asList(
                DRAFT, FILED, "INVESTIGATION", "DECLARE", "RECOGNITION",
                "APPRAISAL", "TREATMENT", "DISPUTE", CLOSED, ARCHIVED)));
        DISPLAY_ORDER.put(TYPE_INJURY, MAIN_CHAIN.get(TYPE_INJURY));

        Map<String, List<String>> injury = new LinkedHashMap<>();
        injury.put(DRAFT, list(FILED));
        injury.put(FILED, list("INVESTIGATION"));
        injury.put("INVESTIGATION", list("DECLARE"));
        injury.put("DECLARE", list("RECOGNITION"));
        injury.put("RECOGNITION", list("APPRAISAL"));
        injury.put("APPRAISAL", list("TREATMENT"));
        // 无争议时可直接结案
        injury.put("TREATMENT", list("DISPUTE", CLOSED));
        injury.put("DISPUTE", list(CLOSED));
        injury.put(CLOSED, list(ARCHIVED));
        FLOW.put(TYPE_INJURY, Collections.unmodifiableMap(injury));

        // ---------- 劳动仲裁 ----------
        MAIN_CHAIN.put(TYPE_ARBITRATION, Collections.unmodifiableList(Arrays.asList(
                DRAFT, FILED, "EVIDENCE", "HEARING", "AWARD", "PERFORM", CLOSED, ARCHIVED)));
        DISPLAY_ORDER.put(TYPE_ARBITRATION, Collections.unmodifiableList(Arrays.asList(
                DRAFT, FILED, "EVIDENCE", "HEARING", "AWARD",
                APPEAL, RETRIAL_HEARING, RULING, "PERFORM", CLOSED, ARCHIVED)));

        Map<String, List<String>> arbitration = new LinkedHashMap<>();
        arbitration.put(DRAFT, list(FILED));
        arbitration.put(FILED, list("EVIDENCE"));
        arbitration.put("EVIDENCE", list("HEARING"));
        // 开庭后按结果分支：裁决或调解
        arbitration.put("HEARING", list("AWARD", "MEDIATION"));
        // 裁决后按当事人态度分支：服从→履行；不服→上诉
        arbitration.put("AWARD", list("PERFORM", APPEAL));
        arbitration.put("MEDIATION", list("PERFORM", CLOSED));
        arbitration.put(APPEAL, list(RETRIAL_HEARING));
        arbitration.put(RETRIAL_HEARING, list(RULING));
        // 裁定与裁决一样，都要走完履行才能结案
        arbitration.put(RULING, list("PERFORM"));
        arbitration.put("PERFORM", list(CLOSED));
        arbitration.put(CLOSED, list(ARCHIVED));
        FLOW.put(TYPE_ARBITRATION, Collections.unmodifiableMap(arbitration));

        // ---------- 关联争议 ----------
        MAIN_CHAIN.put(TYPE_RELATED, Collections.unmodifiableList(Arrays.asList(
                DRAFT, FILED, CLOSED, ARCHIVED)));
        DISPLAY_ORDER.put(TYPE_RELATED, MAIN_CHAIN.get(TYPE_RELATED));

        Map<String, List<String>> related = new LinkedHashMap<>();
        related.put(DRAFT, list(FILED));
        related.put(FILED, list(CLOSED));
        related.put(CLOSED, list(ARCHIVED));
        FLOW.put(TYPE_RELATED, Collections.unmodifiableMap(related));
    }

    private static List<String> list(String... items) {
        return Collections.unmodifiableList(Arrays.asList(items));
    }

    public boolean isSupportedType(String caseType) {
        return FLOW.containsKey(caseType);
    }

    public List<String> mainChain(String caseType) {
        List<String> chain = MAIN_CHAIN.get(caseType);
        return chain == null ? Collections.<String>emptyList() : chain;
    }

    /** 当前状态允许推进到的下一状态；已归档返回空 */
    public List<String> nextStatuses(String caseType, String current) {
        Map<String, List<String>> flow = FLOW.get(caseType);
        if (flow == null || current == null) {
            return Collections.emptyList();
        }
        List<String> next = flow.get(current);
        return next == null ? Collections.<String>emptyList() : next;
    }

    public boolean canTransit(String caseType, String from, String to) {
        return nextStatuses(caseType, from).contains(to);
    }

    /** 退回目标：主链前一个节点；已在首个节点返回 null */
    public String previousStatus(String caseType, String current) {
        List<String> chain = mainChain(caseType);
        int index = chain.indexOf(current);
        if (index <= 0) {
            return null;
        }
        return chain.get(index - 1);
    }

    public boolean canRollback(String caseType, String current) {
        String previous = previousStatus(caseType, current);
        return previous != null && !ARCHIVED.equals(current);
    }

    public boolean isArchived(Integer archiveStatus) {
        return archiveStatus != null && archiveStatus == 1;
    }

    /** 进度百分比，按展示顺序（含分支节点）计算 */
    public int progress(String caseType, String status) {
        List<String> chain = DISPLAY_ORDER.get(caseType);
        int index = chain == null ? -1 : chain.indexOf(status);
        if (index < 0 || chain.isEmpty()) {
            return 0;
        }
        return (int) Math.round((index + 1) * 100.0 / chain.size());
    }

    /** 该类型的全部状态（含分支节点），顺序即办理顺序 */
    public List<String> allStatuses(String caseType) {
        List<String> chain = DISPLAY_ORDER.get(caseType);
        return chain == null ? Collections.<String>emptyList() : new ArrayList<>(chain);
    }
}
