package com.gsms.module.cases.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_instance")
public class WfInstance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String processDefId;
    private String processInstanceId;
    private String businessKey;
    /** RUNNING / COMPLETED / TERMINATED */
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
