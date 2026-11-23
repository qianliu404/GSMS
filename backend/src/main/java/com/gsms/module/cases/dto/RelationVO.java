package com.gsms.module.cases.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 争议关联列表行：一对案件 + 关联说明 */
@Data
public class RelationVO {

    /** 关联记录 ID（双向去重后保留最早一条） */
    private Long id;
    private Long mainCaseId;
    private String mainCaseNo;
    private String mainEmployee;
    private String mainCaseType;
    private String mainCaseTypeName;
    private String mainStatus;
    private String mainStatusName;
    private LocalDate mainOccurDate;

    private Long subCaseId;
    private String subCaseNo;
    private String subEmployee;
    private String subCaseType;
    private String subCaseTypeName;
    private String subStatus;
    private String subStatusName;
    private LocalDate subOccurDate;

    private String relationDesc;
    private LocalDateTime createTime;
    /** 是否至少一方未结案/未归档 */
    private boolean active;
}
