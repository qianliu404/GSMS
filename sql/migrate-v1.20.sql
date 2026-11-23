-- V1.20 休假天数支持小数
ALTER TABLE case_injury_ext
  MODIFY COLUMN rest_days DECIMAL(6,1) DEFAULT NULL COMMENT '鉴定休假天数（天），允许小数';
