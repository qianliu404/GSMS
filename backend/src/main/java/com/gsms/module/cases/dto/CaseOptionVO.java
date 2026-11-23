package com.gsms.module.cases.dto;

import lombok.Data;

import java.time.LocalDate;

/** 案件选择项：新建案件时选中关联案件后回填基本信息 */
@Data
public class CaseOptionVO {

    private Long id;
    private String caseNo;
    private String caseType;
    private String caseTypeName;
    private String status;
    private String statusName;
    private String employeeName;
    private String plantArea;
    private String workshopTeam;
    private String leaderL1;
    private String leaderL2;
    private String handlerId;
    private String handlerName;
    private String departmentId;
    private String departmentName;
    private LocalDate occurDate;
}
