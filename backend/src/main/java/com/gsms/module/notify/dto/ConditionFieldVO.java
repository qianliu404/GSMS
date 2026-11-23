package com.gsms.module.notify.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 条件字段元数据：前端据此渲染下拉框，新增条件只需后端加一条元数据 */
@Data
public class ConditionFieldVO implements Serializable {

    private String code;
    private String label;
    /** TEXT / NUMBER / DATE / ENUM / USER */
    private String type;
    /** ENUM 类型的可选值 */
    private List<Option> options = new ArrayList<>();
    /** 该字段支持的操作符编码 */
    private List<String> operators = new ArrayList<>();

    @Data
    public static class Option implements Serializable {
        private String value;
        private String label;
        public Option() {}
        public Option(String value, String label) { this.value = value; this.label = label; }
    }
}
