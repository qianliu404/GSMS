package com.gsms.module.report.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.gsms.common.R;
import com.gsms.common.UserContext;
import com.gsms.module.cases.dto.CaseQuery;
import com.gsms.module.report.dto.CaseExportRow;
import com.gsms.module.report.dto.InjuryStatRow;
import com.gsms.module.report.dto.ReportDTO;
import com.gsms.module.report.service.ReportService;
import com.gsms.module.report.support.TableSheetHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    /** 统计表列宽：序号、厂区、车间班组、姓名、受伤原因、小班长、大班长、认定、鉴定、三项待遇 */
    private static final int[] STAT_WIDTHS = {7, 13, 24, 11, 50, 11, 11, 17, 19, 17, 17, 17};
    private static final int STAT_REASON_COL = 4;

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/case-summary")
    @PreAuthorize("hasAuthority('report:view')")
    public R<ReportDTO.CaseSummary> caseSummary() {
        return R.ok(reportService.caseSummary());
    }

    @GetMapping("/efficiency")
    @PreAuthorize("hasAuthority('report:view')")
    public R<ReportDTO.Efficiency> efficiency() {
        return R.ok(reportService.efficiency());
    }

    @GetMapping("/amount")
    @PreAuthorize("hasAuthority('report:view')")
    public R<ReportDTO.Amount> amount() {
        return R.ok(reportService.amount());
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('report:view')")
    public R<ReportDTO.Overdue> overdue() {
        return R.ok(reportService.overdue());
    }

    /**
     * 导出工伤事故人员信息统计表（单工作表，不再附带案件清单）。
     * 台账导出走 /api/v1/ledger/export，归档卷宗走 /api/v1/reports/archive/export。
     */
    @GetMapping("/cases/export")
    @PreAuthorize("hasAuthority('report:export')")
    public void exportCases(CaseQuery query, HttpServletResponse response) throws IOException {
        String exportBy = UserContext.get().getUserName();
        ReportService.ExportBundle bundle = reportService.buildExport(query, exportBy);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        prepare(response, "工伤事故人员信息统计表_" + exportBy + "_" + timestamp + ".xlsx", exportBy);

        String title = bundle.getYear() + "年工伤事故人员信息统计表";
        String note = "导出人：" + exportBy
                + "　　导出时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + "　　共 " + bundle.getStats().size() + " 人";

        List<List<Object>> data = new ArrayList<>(bundle.getStats().size() + 3);
        data.add(fillRow(title, STAT_WIDTHS.length));
        data.add(fillRow(note, STAT_WIDTHS.length));
        data.add(new ArrayList<>(InjuryStatRow.headers()));
        for (InjuryStatRow stat : bundle.getStats()) {
            data.add(stat.asCells());
        }

        ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build();
        try {
            WriteSheet sheet = EasyExcel.writerSheet(0, "工伤事故人员信息统计表")
                    .registerWriteHandler(new TableSheetHandler(STAT_WIDTHS, STAT_REASON_COL))
                    .build();
            writer.write(data, sheet);
        } finally {
            writer.finish();
        }
    }

    /** 归档库：导出卷宗目录（单工作表） */
    @GetMapping("/archive/export")
    @PreAuthorize("hasAuthority('report:export')")
    public void exportArchive(CaseQuery query, HttpServletResponse response) throws IOException {
        String exportBy = UserContext.get().getUserName();
        List<CaseExportRow> rows = reportService.archiveRows(query, exportBy);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        prepare(response, "归档卷宗目录_" + exportBy + "_" + timestamp + ".xlsx", exportBy);

        ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build();
        try {
            WriteSheet sheet = EasyExcel.writerSheet(0, "归档卷宗目录")
                    .head(CaseExportRow.class)
                    .build();
            writer.write(rows, sheet);
        } finally {
            writer.finish();
        }
    }

    private void prepare(HttpServletResponse response, String fileName, String exportBy) throws IOException {
        String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setHeader("X-Export-By", URLEncoder.encode(exportBy, "UTF-8"));
    }

    private List<Object> fillRow(String text, int cols) {
        List<Object> row = new ArrayList<>(cols);
        row.add(text);
        for (int i = 1; i < cols; i++) {
            row.add("");
        }
        return row;
    }
}
