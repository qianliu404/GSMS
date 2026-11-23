package com.gsms.module.collab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("countersign")
public class Countersign {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String nodeCode;
    private String countersignerId;
    /** AGREE / DISAGREE / CONDITIONAL */
    private String opinionType;
    private String opinion;
    private LocalDateTime countersignTime;
    /** PASS / REJECT / PENDING */
    private String result;
    private LocalDateTime createTime;
}
