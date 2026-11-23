package com.gsms.module.cases.dto;

import com.gsms.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class CaseQuery extends PageQuery {

    private String caseType;
    private String status;
    private String departmentId;
    private String handlerId;
    /** 案件编号/员工姓名/案件名称 */
    private String keyword;
    /** 发生日期起（yyyy-MM-dd），GET 查询参数需显式声明格式，否则 Spring 会按本地化短日期解析而失败 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate occurDateFrom;
    /** 发生日期止（yyyy-MM-dd） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate occurDateTo;
    /** 仅看超期案件 */
    private Boolean overdue;
    /** 仅看临近超期案件：未超期但 3 天内到期 */
    private Boolean dueSoon;
    private Integer archiveStatus;
}
