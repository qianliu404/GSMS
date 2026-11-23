package com.gsms.module.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 推送历史 */
@Data
@TableName("notify_history")
public class NotifyHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long configId;
    private String configName;
    private LocalDateTime pushTime;
    private Integer matched;
    private String result;
    private String message;
    private String error;
}
