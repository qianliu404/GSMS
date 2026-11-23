-- =============================================================
-- V1.6 迁移：下线案件评论功能
-- 1) 删除 comment 表（评论及 @提醒 记录）
-- 说明：评论 Tab、接口 /api/v1/cases/{id}/comments 及相关代码已移除，
--       回收站永久删除也不再清理该表。
-- =============================================================

DROP TABLE IF EXISTS comment;

-- 校验：应无结果
SELECT '残留评论表' AS chk, COUNT(*) AS cnt
  FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'comment';
