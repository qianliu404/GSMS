package com.gsms.module.deadline.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DeadlineVO {

    private Long id;
    private Long caseId;
    private String caseNo;
    private String caseName;
    private String caseType;
    private String nodeCode;
    private String nodeName;
    private String deadlineType;
    private String deadlineTypeName;
    private LocalDate deadlineDate;
    private Integer remindDays;
    /** 距截止日剩余天数，负数表示已超期 */
    private Long remainDays;
    private String status;
    private String statusName;
    private String handlerId;
    private String handlerName;
    private Integer overdueLevel;
    private String overdueReason;
    private String superviseOpinion;
    private boolean overdue;
    private long overdueDays;
    /** 距截止日不足 7 天：临近超期 */
    private boolean dueSoon;
}
