package com.gsms.module.cases.controller;

import com.gsms.common.R;
import com.gsms.module.cases.dto.RelationCreateRequest;
import com.gsms.module.cases.dto.RelationPageVO;
import com.gsms.module.cases.dto.RelationVO;
import com.gsms.module.cases.service.RelationService;
import com.gsms.module.report.support.TableSheetHandler;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 争议关联：关联关系集中管理、解除与导出 */
@RestController
@RequestMapping("/api/v1/relations")
public class RelationController {

    private final RelationService relationService;

    public RelationController(RelationService relationService) {
        this.relationService = relationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('relation:view')")
    public R<RelationPageVO> page(RelationPageVO.Query query) {
        return R.ok(relationService.page(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('relation:create')")
    public R<Long> create(@Valid @RequestBody RelationCreateRequest request) {
        return R.ok(relationService.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('relation:delete')")
    public R<Void> remove(@PathVariable Long id) {
        relationService.remove(id);
        return R.ok();
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('relation:export')")
    public void export(RelationPageVO.Query query, HttpServletResponse response) throws IOException {
        List<String> headers = Arrays.asList("主案案号", "主案当事人", "主案类型", "主案阶段",
                "关联案案号", "关联案当事人", "关联案类型", "关联案阶段", "关联说明", "建立日期");
        List<List<Object>> rows = new ArrayList<>();
        for (RelationVO vo : relationService.exportRows(query)) {
            rows.add(Arrays.asList(
                    vo.getMainCaseNo(), dash(vo.getMainEmployee()), vo.getMainCaseTypeName(), vo.getMainStatusName(),
                    vo.getSubCaseNo(), dash(vo.getSubEmployee()), vo.getSubCaseTypeName(), vo.getSubStatusName(),
                    dash(vo.getRelationDesc()),
                    vo.getCreateTime() == null ? "" : vo.getCreateTime().toLocalDate().toString()));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String fileName = URLEncoder.encode("争议关联清单_" + timestamp + ".xlsx", "UTF-8").replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        int cols = headers.size();
        List<List<Object>> sheetRows = new ArrayList<>(rows.size() + 3);
        sheetRows.add(fillRow("争议关联清单", cols));
        sheetRows.add(fillRow("导出时间：" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "　　共 " + rows.size() + " 组关联", cols));
        sheetRows.add(new ArrayList<>(headers));
        sheetRows.addAll(rows);

        int[] widths = {16, 12, 14, 12, 16, 12, 14, 12, 40, 14};
        ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build();
        try {
            WriteSheet sheet = EasyExcel.writerSheet(0, "争议关联")
                    .registerWriteHandler(new TableSheetHandler(widths, 8))
                    .build();
            writer.write(sheetRows, sheet);
        } finally {
            writer.finish();
        }
    }

    private List<Object> fillRow(String text, int cols) {
        List<Object> row = new ArrayList<>(cols);
        row.add(text);
        for (int i = 1; i < cols; i++) {
            row.add("");
        }
        return row;
    }

    private String dash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }
}
