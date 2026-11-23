-- v1.15：案件选择器（关联案件 / 争议关联）检索优化
-- 案号已有唯一索引，按姓名模糊检索补一个窄索引，减少全表扫描

SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'case_main'
                  AND index_name = 'idx_employee_name');
SET @sql := IF(@exists = 0,
    'ALTER TABLE case_main ADD INDEX idx_employee_name (employee_name)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
