package com.gsms.module.cases.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("case_status_log")
public class CaseStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String fromStatus;
    private String toStatus;
    private String action;
    private String operatorId;
    private String opinion;
    private LocalDateTime operateTime;
}
