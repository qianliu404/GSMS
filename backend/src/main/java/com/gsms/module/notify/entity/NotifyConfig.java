package com.gsms.module.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 推送配置：自定义周期、日期、字段与目标群 */
@Data
@TableName("notify_config")
public class NotifyConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String configName;
    private Integer enabled;
    /** DAILY / WEEKLY / MONTHLY */
    private String cycleType;
    /** 周推送：1-7 逗号分隔（周一=1），如 1,3,5 */
    private String weekDays;
    /** 月推送：1-31 逗号分隔，如 1,15 */
    private String monthDays;
    /** HH:mm */
    private String pushTime;
    /** 距截止不足 N 天纳入推送 */
    private Integer aheadDays;
    private Integer includeOverdue;
    /** 逗号分隔的字段编码 */
    private String fields;
    /** 自定义推送条件（JSON 数组），为空时使用默认条件 */
    private String conditions;
    private String webhook;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime lastPushTime;
}
