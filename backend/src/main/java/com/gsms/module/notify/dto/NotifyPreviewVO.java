package com.gsms.module.notify.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 推送预览结果：文案 + 命中明细 + 可用字段 */
@Data
public class NotifyPreviewVO {

    /** 生成的消息正文（Markdown） */
    private String content;
    /** 命中案件数 */
    private int matched;
    /** 命中明细，每个元素是一条案件的可选字段值 */
    private List<FieldRow> rows = new ArrayList<>();
    /** 系统支持的全部可选字段 */
    private List<FieldMeta> availableFields = new ArrayList<>();

    @Data
    public static class FieldRow {
        private String caseNo;
        private String caseName;
        private String nodeName;
        private String deadlineDate;
        private Long remainDays;
        private String handlerName;
        private String caseType;
        private String deadlineTypeName;
        private String statusName;
    }

    @Data
    public static class FieldMeta {
        private String code;
        private String label;
        private boolean checked;

        public FieldMeta() {
        }

        public FieldMeta(String code, String label, boolean checked) {
            this.code = code;
            this.label = label;
            this.checked = checked;
        }
    }
}
