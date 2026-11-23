package com.gsms.module.cases.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InjuryExtDTO {

    private LocalDate injuryDate;
    private String injuryPlace;
    private String injuryReason;
    private LocalDate applyDate;
    private LocalDate recognizeDate;
    /** RECOGNIZED / NOT_RECOGNIZED */
    private String recognizeResult;
    private LocalDate appraisalDate;
    private String appraisalLevel;
    /** 鉴定休假天数（天） */
    private BigDecimal restDays;
    private BigDecimal treatmentAmount;
    /** 三项一次性待遇：状态 + 金额 */
    private String subsidyDisabilityStatus;
    private BigDecimal subsidyDisabilityAmount;
    private String subsidyMedicalStatus;
    private BigDecimal subsidyMedicalAmount;
    private String subsidyEmploymentStatus;
    private BigDecimal subsidyEmploymentAmount;
    private Integer disputeFlag;
}
