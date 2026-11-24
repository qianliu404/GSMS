-- V1.21 推送历史区分手动测试记录（测试记录不参与当日去重）
ALTER TABLE notify_history
  ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0 COMMENT '是否手动测试（1=测试推送）' AFTER result;
