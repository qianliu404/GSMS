-- =============================================================
-- v1.10 查询性能：补排序索引
--
-- 背景：case_main 只有 case_type+status / department_id / create_time / handler_id 索引，
-- 而两条主查询的排序字段都没有索引，数据量上来后走全表 filesort：
--   案件列表 CaseService.page  →  ORDER BY update_time DESC
--   台账     LedgerService.page →  ORDER BY occur_date DESC
--
-- 处理：新增 idx_update_time、idx_occur_date。
-- 注意：MySQL 8.0 不支持 ADD INDEX IF NOT EXISTS，本脚本只能执行一次；
--       重复执行会报 1061 (Duplicate key name)，属预期。
-- =============================================================

ALTER TABLE case_main
    ADD INDEX idx_update_time (update_time),
    ADD INDEX idx_occur_date (occur_date);
