package com.gsms.module.audit.controller;

import com.gsms.common.PageResult;
import com.gsms.common.R;
import com.gsms.module.audit.entity.AuditLog;
import com.gsms.module.audit.service.AuditQueryService;
import com.gsms.module.audit.service.AuditRetentionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final AuditQueryService auditQueryService;
    private final AuditRetentionService auditRetentionService;

    public AuditController(AuditQueryService auditQueryService, AuditRetentionService auditRetentionService) {
        this.auditQueryService = auditQueryService;
        this.auditRetentionService = auditRetentionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SYS_ADMIN','ROLE_AUDITOR')")
    public R<PageResult<AuditLog>> page(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(auditQueryService.page(userId, module, operation, result,
                dateFrom, dateTo, keyword, pageNum, pageSize));
    }

    /** 手动清理超过保留期的审计日志（定时任务之外可由管理员立即执行） */
    @PostMapping("/cleanup")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<Integer> cleanup() {
        return R.ok(auditRetentionService.cleanup());
    }
}
