package com.gsms.module.cases.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ArbitrationExtDTO {

    private String arbitrationNo;
    private LocalDate applyDate;
    private LocalDate acceptDate;
    private LocalDateTime hearingDate;
    /** AWARD / MEDIATION / WITHDRAW */
    private String resultType;
    private BigDecimal awardAmount;
    /** 履行款金额 */
    private BigDecimal performAmount;
    /** PENDING / PARTIAL / DONE */
    private String performStatus;
    private LocalDate performDate;
}
