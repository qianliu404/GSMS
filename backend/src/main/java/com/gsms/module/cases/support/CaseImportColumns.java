package com.gsms.module.cases.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 历史案件导入模板的列定义。
 *
 * 模板下载与解析共用同一份定义：改列名只改这里，避免「模板改了但解析没跟上」。
 * 解析时按列名（去掉空格与必填星号）匹配，不依赖用户是否调整了列顺序。
 */
public final class CaseImportColumns {

    private CaseImportColumns() {
    }

    public static class Col {
        private final String key;
        private final String label;
        private final boolean required;
        private final String hint;
        private final String demo;
        private final int width;

        Col(String key, String label, boolean required, String hint, String demo, int width) {
            this.key = key;
            this.label = label;
            this.required = required;
            this.hint = hint;
            this.demo = demo;
            this.width = width;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public boolean isRequired() {
            return required;
        }

        public String getHint() {
            return hint;
        }

        public String getDemo() {
            return demo;
        }

        public int getWidth() {
            return width;
        }
    }

    private static final List<Col> ALL = new ArrayList<>();

    static {
        add("caseType", "案件类型", true, "工伤 / 仲裁", "工伤", 10);
        add("caseNo", "案号", true, "沿用历史案号，不可与系统中已有案号重复", "GY2025001", 16);
        add("employeeName", "当事人姓名", true, "伤者或当事人姓名", "张三", 12);
        add("plantArea", "厂区", false, "如：华东一厂", "华东一厂", 14);
        add("workshopTeam", "车间班组", false, "如：生产一部 · 二车间甲班", "生产一部 · 二车间甲班", 24);
        add("leaderL1", "小班长", false, "直属主管", "李四", 10);
        add("leaderL2", "大班长", false, "上级主管", "王五", 10);
        add("occurDate", "发生日期", true, "yyyy-MM-dd，同时作为截止日期的起算日", "2025-03-08", 13);
        add("currentNode", "当前节点", true, "填该案件当前所处节点，如：立案/申报/鉴定/裁决/履行/结案/归档", "结案", 12);
        add("handler", "承办人", true, "填登录名或姓名，须为系统内已有账号", "zhangsan", 12);
        add("archived", "是否归档", false, "是 / 否；填「是」会直接进归档库（只读）", "否", 10);
        add("closeDate", "结案日期", false, "yyyy-MM-dd，结案/归档案件建议填写", "2025-06-30", 13);
        add("closeType", "结案方式", false, "待遇结清/调解结案/裁决结案/撤诉/其他", "裁决结案", 13);
        add("description", "案件描述", false, "事故经过或争议情况", "操作冲床时右手挤压伤", 30);
        add("injuryReason", "受伤原因", false, "仅工伤案件填", "设备故障导致右手挤压伤", 26);
        add("recognize", "工伤认定情况", false, "已认定/不予认定/申报中/未申报", "已认定", 14);
        add("appraisalLevel", "劳动能力鉴定结论", false, "如：九级、未达级", "九级", 18);
        add("restDays", "休假天数", false, "整数，天", "90", 10);
        add("subsidyDisability", "一次性伤残金金额", false, "元，留空表示未申领", "45000", 18);
        add("subsidyMedical", "一次性医疗金金额", false, "元，留空表示未申领", "12000", 18);
        add("subsidyEmployment", "一次性就业金金额", false, "元，留空表示未申领", "30000", 18);
        add("arbitrationNo", "仲裁案号", false, "仅仲裁案件填", "ZS2025-001", 16);
        add("hearingDate", "开庭时间", false, "yyyy-MM-dd HH:mm，仅仲裁案件填", "2025-05-20 09:30", 17);
        add("resultType", "裁决/调解结果", false, "裁决/调解/撤诉，仅仲裁案件填", "裁决", 15);
        add("awardAmount", "裁决/调解金额", false, "元，仅仲裁案件填", "86000", 15);
        add("performStatus", "履行状态", false, "待履行/履行中/已履行/逾期未履行", "已履行", 13);
        add("performAmount", "履行款", false, "元，仅仲裁案件填", "86000", 13);
    }

    private static void add(String key, String label, boolean required, String hint, String demo, int width) {
        ALL.add(new Col(key, label, required, hint, demo, width));
    }

    public static List<Col> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** 表头文本归一化：去掉空格、必填星号、全角符号，避免用户改格式后匹配不上 */
    public static String normalize(String label) {
        if (label == null) {
            return "";
        }
        return label.replace("*", "")
                .replace("＊", "")
                .replace(" ", "")
                .replace("\u3000", "")
                .replace("／", "/")
                .trim();
    }

    /** 表头别名：用户可能把「当事人姓名」写成「姓名」，两种都认 */
    private static final java.util.Map<String, String> ALIAS = new java.util.HashMap<>();

    static {
        ALIAS.put("姓名", "employeeName");
        ALIAS.put("当事人", "employeeName");
        ALIAS.put("员工姓名", "employeeName");
        ALIAS.put("案件类型", "caseType");
        ALIAS.put("类型", "caseType");
        ALIAS.put("当前节点", "currentNode");
        ALIAS.put("当前状态", "currentNode");
        ALIAS.put("节点", "currentNode");
        ALIAS.put("承办人登录名", "handler");
        ALIAS.put("承办人", "handler");
        ALIAS.put("是否归档", "archived");
        ALIAS.put("归档", "archived");
        ALIAS.put("发生日期", "occurDate");
        ALIAS.put("工伤认定情况", "recognize");
        ALIAS.put("认定情况", "recognize");
        ALIAS.put("劳动能力鉴定结论", "appraisalLevel");
        ALIAS.put("鉴定结论", "appraisalLevel");
        ALIAS.put("一次伤残金金额", "subsidyDisability");
        ALIAS.put("一次性医疗金金额", "subsidyMedical");
        ALIAS.put("一次性就业金金额", "subsidyEmployment");
        ALIAS.put("裁决调解结果", "resultType");
        ALIAS.put("裁决/调解结果", "resultType");
        ALIAS.put("裁决调解金额", "awardAmount");
    }

    /** 表头文本 → 列 key，未识别返回 null */
    public static String keyOf(String label) {
        String text = normalize(label);
        if (ALIAS.containsKey(text)) {
            return ALIAS.get(text);
        }
        for (Col col : ALL) {
            if (normalize(col.getLabel()).equals(text)) {
                return col.getKey();
            }
        }
        return null;
    }
}
