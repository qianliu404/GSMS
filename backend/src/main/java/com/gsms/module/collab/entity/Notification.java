package com.gsms.module.collab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String receiverId;
    /** INNER / EMAIL / WECOM / DINGTALK / FEISHU */
    private String channel;
    private String templateCode;
    private String title;
    private String content;
    private String bizId;
    /** PENDING / SUCCESS / FAIL */
    private String sendStatus;
    private LocalDateTime sendTime;
    private Integer retryCount;
    private Integer readFlag;
    private LocalDateTime createTime;
}
