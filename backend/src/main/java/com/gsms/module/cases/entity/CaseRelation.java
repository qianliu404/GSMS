package com.gsms.module.cases.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("case_relation")
public class CaseRelation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private Long relatedCaseId;
    /** RELATED / ARBITRATION / INJURY */
    private String relationType;
    private String relationDesc;
    private String createBy;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
