package com.gsms.module.audit.service;

import com.gsms.module.audit.mapper.AuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计日志保留期管理：默认只保留最近一年，超期记录自动删除。
 * 每日凌晨执行；服务启动时补做一次，避免长时间停机导致超期记录堆积。
 */
@Slf4j
@Service
public class AuditRetentionService {

    /** 单批最多删除条数，防止一次性大事务 */
    private static final int BATCH_SIZE = 1000;
    /** 单次任务最多处理的批次数，防止异常数据导致任务长时间占用 */
    private static final int MAX_BATCHES = 200;

    private final AuditLogMapper auditLogMapper;

    @Value("${gsms.audit.retention-years:1}")
    private int retentionYears;

    public AuditRetentionService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /** 启动时异步补做一次，不阻塞应用就绪 */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOnStart() {
        try {
            cleanup();
        } catch (Exception e) {
            log.error("启动时清理过期审计日志失败", e);
        }
    }

    /** 每天 02:30 清理一次 */
    @Scheduled(cron = "0 30 2 * * ?")
    public void scheduledCleanup() {
        try {
            cleanup();
        } catch (Exception e) {
            log.error("定时清理过期审计日志失败", e);
        }
    }

    /** 删除超过保留期的审计日志，返回实际删除条数 */
    public int cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusYears(retentionYears);
        long before = auditLogMapper.countExpired(cutoff);
        if (before == 0) {
            return 0;
        }
        int deleted = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int rows = auditLogMapper.deleteExpired(cutoff, BATCH_SIZE);
            deleted += rows;
            if (rows < BATCH_SIZE) {
                break;
            }
        }
        log.info("清理过期审计日志完成: 保留 {} 年, 截止时间 {}, 应清理 {} 条, 实际删除 {} 条",
                retentionYears, cutoff, before, deleted);
        return deleted;
    }
}
