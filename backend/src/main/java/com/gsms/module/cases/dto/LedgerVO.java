package com.gsms.module.cases.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 工伤台账行：字段与详情页「工伤档案信息」保持一致 */
@Data
public class LedgerVO {

    private Long id;
    /** INJURY / ARBITRATION */
    private String caseType;
    private String caseTypeName;
    private String caseNo;
    private String plantArea;
    private String workshopTeam;
    /** 按角色脱敏后的姓名 */
    private String employeeName;
    private String departmentName;
    /** 发生日期（案件时间轴的起算日） */
    private LocalDate occurDate;
    /** 案件描述 */
    private String description;
    private String leaderL1;
    private String leaderL2;
    /** 受伤原因 */
    private String injuryReason;
    private LocalDate injuryDate;
    private String status;
    private String statusName;
    /** 工伤认定情况：未申报/申报中/已认定/不予认定 */
    private String recognizeResult;
    private String recognizeResultName;
    /** 劳动能力鉴定结论，如：九级 */
    private String appraisalLevel;
    /** 鉴定休假天数（天），允许小数 */
    private BigDecimal restDays;
    /** 三项一次性待遇 */
    private String subsidyDisabilityStatus;
    private BigDecimal subsidyDisabilityAmount;
    private String subsidyMedicalStatus;
    private BigDecimal subsidyMedicalAmount;
    private String subsidyEmploymentStatus;
    private BigDecimal subsidyEmploymentAmount;
    /** 金额合计（无权限时为 null） */
    private BigDecimal subsidyTotal;

    /* ---------- 劳动仲裁扩展 ---------- */
    /** 争议事由（案件描述） */
    private String disputeReason;
    private String arbitrationNo;
    private LocalDate applyDate;
    private LocalDate acceptDate;
    /** 开庭时间：yyyy-MM-dd HH:mm */
    private String hearingDate;
    private String resultType;
    private String resultTypeName;
    /** 裁决/调解金额（无权限为 null） */
    private BigDecimal awardAmount;
    private String performStatus;
    private String performStatusName;
    private LocalDate performDate;
    /** 履行款（无权限为 null） */
    private BigDecimal performAmount;
    private String closeTypeName;
    private String closeDate;

    /* ---------- 通用 ---------- */
    private String handlerName;
    /** 无权限查看金额 */
    private boolean amountMasked;
}
