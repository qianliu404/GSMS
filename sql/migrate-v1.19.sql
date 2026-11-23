-- V1.19 推送条件自定义：配置表增加 conditions（JSON），消息改为图片推送
ALTER TABLE notify_config
  ADD COLUMN conditions TEXT DEFAULT NULL COMMENT '自定义推送条件（JSON 数组），为空时使用默认条件' AFTER `fields`;

-- 兼容旧配置：把原有的 ahead_days/include_overdue 口径转成默认条件 JSON
UPDATE notify_config
SET conditions = JSON_ARRAY(
  JSON_OBJECT('field', 'status', 'op', 'NE', 'value', 'CLOSED'),
  JSON_OBJECT('field', 'deadlineType', 'op', 'IN', 'values', JSON_ARRAY('LEGAL', 'INTERNAL')),
  JSON_OBJECT('field', 'remainDays', 'op', 'LTE', 'value', CAST(ahead_days AS CHAR))
)
WHERE conditions IS NULL;
