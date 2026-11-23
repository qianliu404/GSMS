-- =============================================================
-- v1.13 节点计时开启：提交后才开始计算超期
--
-- 背景：截止日期按「发生日期 + 节点时限」推算，但只有承办人在办理区
--       点击「提交」开启当前节点后，才按截止日期判断超期。
--       未开启的节点只显示截止日期，不产生超期/临近超期提醒。
--
-- 处理：deadline_reminder 增加 start_time（开启时间），为空表示未开启；
--       存量未完成记录回填 create_time，保持现网告警口径不变。
-- 注意：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，本脚本只能执行一次。
-- =============================================================

ALTER TABLE deadline_reminder
    ADD COLUMN start_time DATETIME NULL COMMENT '节点开始计时时间（点击提交开启），为空表示未开启' AFTER deadline_date;

UPDATE deadline_reminder
SET start_time = create_time
WHERE start_time IS NULL
  AND status <> 'DONE';
