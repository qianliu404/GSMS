package com.gsms.module.cases.controller;

import com.gsms.common.R;
import com.gsms.module.cases.service.CaseImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * 历史案件批量导入。
 *
 * 与案件主接口分开挂载，避免 /cases/{id} 把 "import" 当成 id 解析。
 */
@RestController
@RequestMapping("/api/v1/case-import")
public class CaseImportController {

    private final CaseImportService importService;

    public CaseImportController(CaseImportService importService) {
        this.importService = importService;
    }

    /** 下载导入模板（含示例行与填写说明） */
    @GetMapping("/template")
    @PreAuthorize("hasAnyAuthority('ROLE_SYS_ADMIN','ROLE_CASE_ADMIN')")
    public void template(HttpServletResponse response) throws IOException {
        importService.writeTemplate(response);
    }

    /** 预检：只解析校验并返回每行错误，不写库 */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyAuthority('ROLE_SYS_ADMIN','ROLE_CASE_ADMIN')")
    public R<Map<String, Object>> preview(@RequestParam("file") MultipartFile file) {
        return R.ok(importService.preview(file));
    }

    /** 确认导入：再校验一次，全部通过后整批写入 */
    @PostMapping("/confirm")
    @PreAuthorize("hasAnyAuthority('ROLE_SYS_ADMIN','ROLE_CASE_ADMIN')")
    public R<Map<String, Object>> confirm(@RequestParam("file") MultipartFile file) {
        return R.ok(importService.confirm(file));
    }
}
