-- =============================================================
-- v1.11 节点办理要素：鉴定休假天数、履行款
--
-- 背景：鉴定节点需要记录本次休假天数，履行节点需要记录实际履行款，
--       两者都要进入案件档案（详情页「档案信息」与台账口径）。
--
-- 处理：为两张扩展表各加一列，均可空，不影响历史数据。
-- 注意：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，本脚本只能执行一次；
--       重复执行会报 1060 (Duplicate column name)，属预期。
-- =============================================================

ALTER TABLE case_injury_ext
    ADD COLUMN rest_days INT NULL COMMENT '鉴定休假天数（天）' AFTER appraisal_level;

ALTER TABLE case_arbitration_ext
    ADD COLUMN perform_amount DECIMAL(14,2) NULL COMMENT '履行款金额' AFTER award_amount;
