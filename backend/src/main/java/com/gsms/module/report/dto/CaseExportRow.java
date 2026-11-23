package com.gsms.module.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
public class CaseExportRow {

    @ExcelProperty("案件编号")
    @ColumnWidth(18)
    private String caseNo;

    @ExcelProperty("案件名称")
    @ColumnWidth(32)
    private String caseName;

    @ExcelProperty("案件类型")
    @ColumnWidth(14)
    private String caseTypeName;

    @ExcelProperty("员工姓名")
    @ColumnWidth(12)
    private String employeeName;

    @ExcelProperty("部门")
    @ColumnWidth(16)
    private String departmentName;

    @ExcelProperty("发生日期")
    @ColumnWidth(14)
    private String occurDate;

    @ExcelProperty("当前状态")
    @ColumnWidth(14)
    private String statusName;

    @ExcelProperty("承办人")
    @ColumnWidth(12)
    private String handlerName;

    @ExcelProperty("结案日期")
    @ColumnWidth(14)
    private String closeDate;

    /** 导出留痕：每行携带导出人与时间，便于追溯外发文件 */
    @ExcelProperty("导出人")
    @ColumnWidth(12)
    private String exportBy;

    @ExcelProperty("导出时间")
    @ColumnWidth(20)
    private String exportTime;
}
