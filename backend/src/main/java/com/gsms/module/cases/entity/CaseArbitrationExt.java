package com.gsms.module.cases.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("case_arbitration_ext")
public class CaseArbitrationExt {

    @TableId
    private Long caseId;
    private String arbitrationNo;
    private LocalDate applyDate;
    private LocalDate acceptDate;
    private LocalDateTime hearingDate;
    /** AWARD / MEDIATION / WITHDRAW */
    private String resultType;
    /**
     * 裁决/调解金额。文档要求"加密/脱敏"，但字段类型为 DECIMAL 且报表中心需按金额聚合，
     * 密文无法 SUM 也无法走索引，故此处保留数值明文，仅做展示层脱敏 + 审计记录脱敏值。
     */
    private BigDecimal awardAmount;
    /** 履行款金额：履行节点办结时登记 */
    private BigDecimal performAmount;
    private String performStatus;
    private LocalDate performDate;
}
