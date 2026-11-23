package com.gsms.module.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gsms.common.PageResult;
import com.gsms.module.audit.entity.AuditLog;
import com.gsms.module.audit.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
public class AuditQueryService {

    private final AuditLogMapper auditLogMapper;

    public AuditQueryService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public PageResult<AuditLog> page(String userId, String module, String operation, String result,
                                     LocalDate dateFrom, LocalDate dateTo, String keyword,
                                     int pageNum, int pageSize) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(userId), AuditLog::getUserId, userId)
                .eq(StringUtils.hasText(module), AuditLog::getModule, module)
                .eq(StringUtils.hasText(result), AuditLog::getResult, result)
                .like(StringUtils.hasText(operation), AuditLog::getOperation, operation)
                .ge(dateFrom != null, AuditLog::getOperateTime,
                        dateFrom == null ? null : dateFrom.atStartOfDay())
                .le(dateTo != null, AuditLog::getOperateTime,
                        dateTo == null ? null : dateTo.atTime(23, 59, 59))
                .orderByDesc(AuditLog::getId);

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(AuditLog::getBusinessId, kw)
                    .or().like(AuditLog::getOperation, kw)
                    .or().like(AuditLog::getUserName, kw));
        }
        int size = Math.min(Math.max(pageSize, 1), 100);
        Page<AuditLog> page = auditLogMapper.selectPage(new Page<>(Math.max(pageNum, 1), size), wrapper);
        List<AuditLog> records = page.getRecords();
        return PageResult.of(page, records);
    }
}
