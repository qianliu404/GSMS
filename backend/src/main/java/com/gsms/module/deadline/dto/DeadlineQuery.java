package com.gsms.module.deadline.dto;

import com.gsms.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeadlineQuery extends PageQuery {

    private Long caseId;
    private String status;
    /** LEGAL / INTERNAL */
    private String deadlineType;
    private Boolean overdue;
    /** 期限日期起（yyyy-MM-dd） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;
    /** 期限日期止（yyyy-MM-dd） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
