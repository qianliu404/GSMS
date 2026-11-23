package com.gsms.module.cases.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 台账可选字段目录。前端表格列与导出表头共用此顺序与名称，
 * 保证「看到什么就导出什么」，避免两边文案漂移。
 */
public final class LedgerColumns {

    private LedgerColumns() {
    }

    /** 单列定义：key、表头、列宽（字符）、默认是否显示、适用范围 */
    public static class Column {
        private final String key;
        private final String label;
        private final int width;
        private final boolean defaultVisible;
        private final String scope;

        Column(String key, String label, int width, boolean defaultVisible, String scope) {
            this.key = key;
            this.label = label;
            this.width = width;
            this.defaultVisible = defaultVisible;
            this.scope = scope;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public int getWidth() {
            return width;
        }

        public boolean isDefaultVisible() {
            return defaultVisible;
        }

        public String getScope() {
            return scope;
        }
    }

    private static final Map<String, Column> ALL = new LinkedHashMap<>();

    static {
        add("seq", "序号", 7, true, "BOTH");
        add("type", "类型", 8, true, "BOTH");
        add("caseNo", "案号", 16, true, "BOTH");
        add("plantArea", "厂区", 11, true, "BOTH");
        add("workshopTeam", "车间班组 / 部门", 24, true, "BOTH");
        add("employeeName", "姓名", 9, true, "BOTH");
        add("occurDate", "发生日期", 12, false, "BOTH");
        add("description", "案件描述", 46, false, "BOTH");
        add("injuryDesc", "受伤原因及日期", 46, true, "INJURY");
        add("disputeReason", "争议事由", 46, true, "ARBITRATION");
        add("leaderL1", "小班长", 9, false, "INJURY");
        add("leaderL2", "大班长", 9, false, "INJURY");
        add("recognize", "工伤认定情况", 15, true, "INJURY");
        add("appraisal", "劳动能力鉴定结论", 16, true, "INJURY");
        add("restDays", "休假天数", 11, true, "INJURY");
        add("subsidyDisability", "一次性伤残金申领", 17, false, "INJURY");
        add("subsidyMedical", "一次性医疗金申领", 17, false, "INJURY");
        add("subsidyEmployment", "一次性就业金申领", 17, false, "INJURY");
        add("arbitrationNo", "仲裁案号", 16, false, "ARBITRATION");
        add("applyDate", "申请日期", 12, false, "ARBITRATION");
        add("acceptDate", "受理日期", 12, false, "ARBITRATION");
        add("hearingDate", "开庭时间", 17, false, "ARBITRATION");
        add("resultType", "结案方式", 11, true, "BOTH");
        add("awardAmount", "裁决/调解金额", 14, false, "ARBITRATION");
        add("performStatus", "履行状态", 11, false, "ARBITRATION");
        add("performAmount", "履行款", 14, true, "ARBITRATION");
        add("handlerName", "承办人", 9, false, "BOTH");
        add("statusName", "当前状态", 11, true, "BOTH");
        add("closeDate", "结案日期", 12, true, "BOTH");
    }

    private static void add(String key, String label, int width, boolean defaultVisible, String scope) {
        ALL.put(key, new Column(key, label, width, defaultVisible, scope));
    }

    public static List<Column> catalog() {
        return new ArrayList<>(ALL.values());
    }

    public static Column get(String key) {
        return ALL.get(key);
    }

    /** 不传 columns 时的默认列，与前端「全部」视图默认列一致 */
    public static List<String> defaultKeys() {
        List<String> keys = new ArrayList<>();
        for (Column column : ALL.values()) {
            if (column.isDefaultVisible()) {
                keys.add(column.getKey());
            }
        }
        return keys;
    }

    /** 解析前端传入的列参数：忽略未知 key，去重并保持前端顺序；为空则用默认列 */
    public static List<Column> resolve(String keys) {
        List<Column> result = new ArrayList<>();
        if (keys == null || keys.trim().isEmpty()) {
            for (String key : defaultKeys()) {
                result.add(ALL.get(key));
            }
            return result;
        }
        for (String raw : Arrays.asList(keys.split(","))) {
            Column column = ALL.get(raw.trim());
            if (column != null && !result.contains(column)) {
                result.add(column);
            }
        }
        if (result.isEmpty()) {
            for (String key : defaultKeys()) {
                result.add(ALL.get(key));
            }
        }
        return result;
    }
}
