package com.gsms.module.cases.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("case_injury_ext")
public class CaseInjuryExt {

    @TableId
    private Long caseId;
    private LocalDate injuryDate;
    private String injuryPlace;
    private String injuryReason;
    private LocalDate applyDate;
    private LocalDate recognizeDate;
    /** RECOGNIZED / NOT_RECOGNIZED */
    private String recognizeResult;
    private LocalDate appraisalDate;
    private String appraisalLevel;
    /** 鉴定休假天数（天），允许小数（如 0.5 天） */
    private BigDecimal restDays;
    /**
     * 待遇金额。与裁决金额同理：报表需聚合，保留数值明文，展示层按角色脱敏。
     */
    private BigDecimal treatmentAmount;
    /** 一次性伤残补助金申领状态 NOT_APPLIED/APPLYING/RECEIVED/INELIGIBLE */
    private String subsidyDisabilityStatus;
    private BigDecimal subsidyDisabilityAmount;
    /** 一次性工伤医疗补助金 */
    private String subsidyMedicalStatus;
    private BigDecimal subsidyMedicalAmount;
    /** 一次性伤残就业补助金 */
    private String subsidyEmploymentStatus;
    private BigDecimal subsidyEmploymentAmount;
    private Integer disputeFlag;
}
