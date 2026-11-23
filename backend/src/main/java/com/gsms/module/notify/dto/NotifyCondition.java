package com.gsms.module.notify.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 可自定义推送条件。多个条件默认 AND 组合。
 * field 取值为后端白名单编码，op 取值为操作符白名单，value 由前端传入并做参数绑定，杜绝 SQL 注入。
 */
@Data
public class NotifyCondition implements Serializable {

    /** 条件字段编码，见 NotifyService.CONDITION_FIELDS */
    private String field;
    /** 操作符：EQ/NE/CONTAINS/NOT_CONTAINS/IN/NOT_IN/GT/GTE/LT/LTE/IS_EMPTY/NOT_EMPTY */
    private String op;
    /** 单值（EQ/CONTAINS/GT 等） */
    private String value;
    /** 多值（IN/NOT_IN），逗号分隔亦可 */
    private List<String> values;

    /** 条件组（预留 OR 分组扩展） */
    private List<NotifyCondition> children = new ArrayList<>();
}
