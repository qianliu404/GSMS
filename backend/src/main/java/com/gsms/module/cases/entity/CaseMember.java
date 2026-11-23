package com.gsms.module.cases.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("case_member")
public class CaseMember {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String userId;
    /** OWNER / HANDLER / CO_HANDLER / COUNTERSIGN / READER */
    private String memberRole;
    private LocalDateTime joinTime;
}
