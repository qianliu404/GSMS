package com.gsms.module.collab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String taskType;
    private String assignerId;
    private String assigneeId;
    private LocalDateTime deadline;
    /** HIGH / MEDIUM / LOW */
    private String priority;
    /** PENDING / DONE / OVERDUE */
    private String status;
    private LocalDateTime finishTime;
    private String result;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
