package com.gsms.module.cases.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 枚举中文名映射。与 sys_dict 初始化数据保持一致，
 * 字典变更时需同步修改此处（前端展示优先取字典接口）。
 */
public final class CaseDict {

    private CaseDict() {
    }

    public static final Map<String, String> TYPE_NAME;
    public static final Map<String, String> STATUS_NAME;
    public static final Map<String, String> MEMBER_ROLE_NAME;
    public static final Map<String, String> RELATION_TYPE_NAME;
    public static final Map<String, String> CLOSE_TYPE_NAME;
    public static final Map<String, String> SUBSIDY_STATUS_NAME;
    public static final Map<String, String> RECOGNIZE_NAME;
    /** 节点配置里自定义的状态码 → 节点名称，运行期由 WorkflowNodeService 注册 */
    private static final Map<String, String> CUSTOM_STATUS_NAME = new ConcurrentHashMap<>();

    static {
        Map<String, String> type = new LinkedHashMap<>();
        type.put("INJURY", "工伤案件");
        type.put("ARBITRATION", "劳动仲裁案件");
        type.put("RELATED", "关联争议案件");
        TYPE_NAME = Collections.unmodifiableMap(type);

        Map<String, String> status = new LinkedHashMap<>();
        status.put("NONE", "—");
        status.put("DRAFT", "草稿");
        status.put("FILED", "立案");
        status.put("INVESTIGATION", "调查取证");
        status.put("DECLARE", "申报");
        status.put("RECOGNITION", "认定");
        status.put("APPRAISAL", "鉴定");
        status.put("TREATMENT", "待遇处理");
        status.put("DISPUTE", "争议处理");
        status.put("EVIDENCE", "证据准备");
        status.put("HEARING", "开庭");
        status.put("AWARD", "裁决");
        status.put("MEDIATION", "调解");
        status.put("PERFORM", "履行");
        status.put("APPEAL", "上诉");
        status.put("RETRIAL_HEARING", "开庭（二审）");
        status.put("RULING", "裁定");
        status.put("CLOSED", "结案");
        status.put("ARCHIVED", "归档");
        STATUS_NAME = Collections.unmodifiableMap(status);

        Map<String, String> memberRole = new LinkedHashMap<>();
        memberRole.put("OWNER", "负责人");
        memberRole.put("HANDLER", "承办人");
        memberRole.put("CO_HANDLER", "协办人");
        memberRole.put("COUNTERSIGN", "会签人");
        memberRole.put("READER", "阅知人");
        MEMBER_ROLE_NAME = Collections.unmodifiableMap(memberRole);

        Map<String, String> relationType = new LinkedHashMap<>();
        relationType.put("RELATED", "关联争议");
        relationType.put("ARBITRATION", "劳动仲裁");
        relationType.put("INJURY", "工伤");
        RELATION_TYPE_NAME = Collections.unmodifiableMap(relationType);

        Map<String, String> closeType = new LinkedHashMap<>();
        closeType.put("TREATMENT_SETTLED", "待遇结清");
        closeType.put("MEDIATION", "调解结案");
        closeType.put("AWARD", "裁决结案");
        closeType.put("WITHDRAW", "撤诉");
        closeType.put("OTHER", "其他");
        CLOSE_TYPE_NAME = Collections.unmodifiableMap(closeType);

        Map<String, String> subsidyStatus = new LinkedHashMap<>();
        subsidyStatus.put("NOT_APPLIED", "未申领");
        subsidyStatus.put("APPLYING", "申领中");
        subsidyStatus.put("RECEIVED", "已到账");
        subsidyStatus.put("INELIGIBLE", "不符合条件");
        SUBSIDY_STATUS_NAME = Collections.unmodifiableMap(subsidyStatus);

        Map<String, String> recognize = new LinkedHashMap<>();
        recognize.put("RECOGNIZED", "已认定（工伤）");
        recognize.put("NOT_RECOGNIZED", "不予认定");
        recognize.put("DECLARING", "申报中");
        recognize.put("NOT_DECLARED", "未申报");
        RECOGNIZE_NAME = Collections.unmodifiableMap(recognize);
    }

    /** 工伤认定情况：空值按「未申报」展示 */
    public static String recognizeName(String code) {
        return code == null ? "未申报" : RECOGNIZE_NAME.getOrDefault(code, code);
    }

    public static String closeTypeName(String code) {
        return code == null ? null : CLOSE_TYPE_NAME.getOrDefault(code, code);
    }

    public static String subsidyStatusName(String code) {
        return code == null ? "未申领" : SUBSIDY_STATUS_NAME.getOrDefault(code, code);
    }

    public static String typeName(String code) {
        return TYPE_NAME.getOrDefault(code, code);
    }

    public static String statusName(String code) {
        if (code == null) {
            return null;
        }
        // 节点表里的名称优先（支持用户自定义状态），未登记时回落内置字典
        String custom = CUSTOM_STATUS_NAME.get(code);
        if (custom != null && !custom.isEmpty()) {
            return custom;
        }
        return STATUS_NAME.getOrDefault(code, code);
    }

    /** 注册（或覆盖）自定义状态名，供节点配置保存后刷新展示 */
    public static void registerStatus(String code, String name) {
        if (code == null || code.isEmpty() || name == null || name.isEmpty()) {
            return;
        }
        CUSTOM_STATUS_NAME.put(code, name);
    }

    /** 清空自定义状态名（重新从节点表装载前调用） */
    public static void clearCustomStatus() {
        CUSTOM_STATUS_NAME.clear();
    }

    public static String memberRoleName(String code) {
        return MEMBER_ROLE_NAME.getOrDefault(code, code);
    }
}
