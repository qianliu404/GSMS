package com.gsms.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String userName;
    private String operation;
    private String module;
    private String businessId;
    private String requestIp;
    /** 脱敏后的请求参数 */
    private String requestParams;
    /** SUCCESS / FAIL */
    private String result;
    private String traceId;
    private String beforeData;
    private String afterData;
    private LocalDateTime operateTime;
}
