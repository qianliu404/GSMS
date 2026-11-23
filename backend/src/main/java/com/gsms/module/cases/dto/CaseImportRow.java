package com.gsms.module.cases.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史案件导入行。
 *
 * 前半部分是 Excel 原始文本（便于回显错误行），后半部分是校验通过后的解析结果。
 * 校验不通过的行只收集 errors，不写入任何数据。
 */
@Data
public class CaseImportRow {

    /** Excel 行号（含表头首行，报错时直接指给用户看） */
    private int rowNum;

    /* ---------- Excel 原始值 ---------- */
    private String caseTypeText;
    private String caseNo;
    private String employeeName;
    private String plantArea;
    private String workshopTeam;
    private String leaderL1;
    private String leaderL2;
    private String occurDateText;
    private String currentNodeText;
    private String handlerText;
    private String archivedText;
    private String closeDateText;
    private String closeTypeText;
    private String description;
    private String injuryReason;
    private String recognizeText;
    private String appraisalLevel;
    private String restDaysText;
    private String arbitrationNo;
    private String hearingDateText;
    private String resultTypeText;
    private String awardAmountText;
    private String performStatusText;
    private String performAmountText;
    private String subsidyDisabilityText;
    private String subsidyMedicalText;
    private String subsidyEmploymentText;

    /* ---------- 解析结果 ---------- */
    private String caseType;
    private String status;
    private boolean archiveFlag;
    private String handlerId;
    private String closeType;
    private LocalDate occurDate;
    private LocalDate closeDate;
    private LocalDateTime hearingDate;
    private String recognizeCode;
    private String resultCode;
    private String performCode;
    private BigDecimal restDays;
    private BigDecimal awardAmount;
    private BigDecimal performAmount;
    private BigDecimal subsidyDisabilityAmount;
    private BigDecimal subsidyMedicalAmount;
    private BigDecimal subsidyEmploymentAmount;

    /** 该行的校验错误，空表示可导入 */
    private final List<String> errors = new ArrayList<>();
}
