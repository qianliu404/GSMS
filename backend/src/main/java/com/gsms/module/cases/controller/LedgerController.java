package com.gsms.module.cases.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.gsms.common.PageResult;
import com.gsms.common.R;
import com.gsms.common.UserContext;
import com.gsms.module.cases.dto.LedgerQuery;
import com.gsms.module.cases.dto.LedgerVO;
import com.gsms.module.cases.service.LedgerService;
import com.gsms.module.cases.support.LedgerColumns;
import com.gsms.module.report.support.TableSheetHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ledger:view')")
    public R<PageResult<LedgerVO>> page(LedgerQuery query) {
        return R.ok(ledgerService.page(query));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ledger:view')")
    public R<Map<String, Object>> summary() {
        return R.ok(ledgerService.summary());
    }

    /** 可选字段目录：前端「字段设置」与导出表头共用同一套定义 */
    @GetMapping("/columns")
    @PreAuthorize("hasAuthority('ledger:view')")
    public R<List<LedgerColumns.Column>> columns() {
        return R.ok(LedgerColumns.catalog());
    }

    /**
     * 导出台账：列与顺序取当前表格显示的字段（columns 逗号分隔），
     * 因此导出文件的表头与界面完全一致。
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('ledger:export')")
    public void export(LedgerQuery query,
                       @RequestParam(value = "columns", required = false) String columns,
                       HttpServletResponse response) throws IOException {
        Map<String, Object> data = ledgerService.exportData(query, columns);
        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) data.get("headers");
        @SuppressWarnings("unchecked")
        List<Integer> widths = (List<Integer>) data.get("widths");
        @SuppressWarnings("unchecked")
        List<List<Object>> rows = (List<List<Object>>) data.get("rows");

        String exportBy = UserContext.get().getUserName();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String fileName = URLEncoder.encode("台账_" + exportBy + "_" + timestamp + ".xlsx", "UTF-8")
                .replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.setHeader("X-Export-By", URLEncoder.encode(exportBy, "UTF-8"));

        int cols = Math.max(1, headers.size());
        List<List<Object>> sheetRows = new ArrayList<>(rows.size() + 3);
        sheetRows.add(fillRow("工伤及劳动仲裁台账", cols));
        sheetRows.add(fillRow("导出人：" + exportBy
                + "　　导出时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + "　　共 " + rows.size() + " 条", cols));
        sheetRows.add(new ArrayList<>(headers));
        sheetRows.addAll(rows);

        int[] widthArray = new int[cols];
        for (int i = 0; i < cols; i++) {
            widthArray[i] = i < widths.size() ? widths.get(i) : 14;
        }
        // 长文本列（受伤原因 / 争议事由）左对齐换行；未显示时按 -1 处理
        int wrapCol = headers.indexOf("受伤原因及日期");
        if (wrapCol < 0) {
            wrapCol = headers.indexOf("争议事由");
        }

        ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build();
        try {
            WriteSheet sheet = EasyExcel.writerSheet(0, "台账")
                    .registerWriteHandler(new TableSheetHandler(widthArray, wrapCol))
                    .build();
            writer.write(sheetRows, sheet);
        } finally {
            writer.finish();
        }
    }

    /** 更新一次性待遇申领进度：key 取 DISABILITY/MEDICAL/EMPLOYMENT */
    @PutMapping("/cases/{caseId}/subsidy")
    @PreAuthorize("hasAuthority('ledger:edit')")
    public R<Void> updateSubsidy(@PathVariable Long caseId,
                                 @RequestBody Map<String, Object> body) {
        String key = body.get("key") == null ? null : String.valueOf(body.get("key"));
        String status = body.get("status") == null ? null : String.valueOf(body.get("status"));
        BigDecimal amount = body.get("amount") == null ? null : new BigDecimal(String.valueOf(body.get("amount")));
        ledgerService.updateSubsidy(caseId, key, status, amount);
        return R.ok();
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
