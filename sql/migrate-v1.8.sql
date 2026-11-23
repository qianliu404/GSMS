-- =============================================================
--  V1.8  劳动仲裁「裁决」后分支：服从 → 履行 → 结案；不服 → 上诉 → 开庭 → 裁定 → 结案
--        同时为流程节点增加可配置的后继节点（可自定义分支）
--  执行：mysql -u case_app -p case_system < migrate-v1.8.sql
--  幂等：ALTER 用 IF NOT EXISTS 语法不支持，重复执行会报 1060，可忽略
-- =============================================================

-- 1) 节点表增加后继节点配置（逗号分隔的状态码；为空则沿用内置拓扑）
ALTER TABLE wf_node_def
  ADD COLUMN next_codes VARCHAR(200) DEFAULT NULL COMMENT '允许的后继节点码，逗号分隔；空则按内置流程' AFTER allow_rollback;

-- 2) 仲裁流程插入上诉分支节点，并顺延后续节点顺序
UPDATE wf_node_def SET sort_no = 11 WHERE flow_key = 'ARBITRATION' AND status_code = 'ARCHIVED';
UPDATE wf_node_def SET sort_no = 10 WHERE flow_key = 'ARBITRATION' AND status_code = 'CLOSED';
UPDATE wf_node_def SET sort_no = 9  WHERE flow_key = 'ARBITRATION' AND status_code = 'PERFORM';

INSERT INTO wf_node_def
  (flow_key, status_code, node_name, sort_no, time_limit, main_role, key_material, sys_action, allow_rollback, next_codes, enabled)
VALUES
  ('ARBITRATION', 'APPEAL', '上诉', 6, '15 天', '法务专员 / 代理律师', '上诉状、一审裁决书、送达回证', '生成上诉待办，启动上诉期计时', 1, 'RETRIAL_HEARING', 1),
  ('ARBITRATION', 'RETRIAL_HEARING', '开庭（二审）', 7, '30 天', '法务专员 / 代理律师', '开庭通知、庭审笔录、证据清单', '登记开庭时间，生成庭审待办', 1, 'RULING', 1),
  ('ARBITRATION', 'RULING', '裁定', 8, '15 天', '法务专员', '二审裁定书', '登记裁定结果，可进入履行/结案', 1, 'CLOSED', 1)
ON DUPLICATE KEY UPDATE
  node_name = VALUES(node_name), sort_no = VALUES(sort_no), next_codes = VALUES(next_codes);

-- 3) 仲裁流程各节点写入后继（含「裁决」分支：履行 or 上诉）
UPDATE wf_node_def SET next_codes = 'FILED'            WHERE flow_key = 'ARBITRATION' AND status_code = 'DRAFT';
UPDATE wf_node_def SET next_codes = 'EVIDENCE'         WHERE flow_key = 'ARBITRATION' AND status_code = 'FILED';
UPDATE wf_node_def SET next_codes = 'HEARING'          WHERE flow_key = 'ARBITRATION' AND status_code = 'EVIDENCE';
UPDATE wf_node_def SET next_codes = 'AWARD,MEDIATION'  WHERE flow_key = 'ARBITRATION' AND status_code = 'HEARING';
UPDATE wf_node_def SET next_codes = 'PERFORM,APPEAL'   WHERE flow_key = 'ARBITRATION' AND status_code = 'AWARD';
UPDATE wf_node_def SET next_codes = 'PERFORM,CLOSED'   WHERE flow_key = 'ARBITRATION' AND status_code = 'MEDIATION';
UPDATE wf_node_def SET next_codes = 'RETRIAL_HEARING'  WHERE flow_key = 'ARBITRATION' AND status_code = 'APPEAL';
UPDATE wf_node_def SET next_codes = 'RULING'           WHERE flow_key = 'ARBITRATION' AND status_code = 'RETRIAL_HEARING';
UPDATE wf_node_def SET next_codes = 'CLOSED'           WHERE flow_key = 'ARBITRATION' AND status_code = 'RULING';
UPDATE wf_node_def SET next_codes = 'CLOSED'           WHERE flow_key = 'ARBITRATION' AND status_code = 'PERFORM';
UPDATE wf_node_def SET next_codes = 'ARCHIVED'         WHERE flow_key = 'ARBITRATION' AND status_code = 'CLOSED';

-- 4) 裁决节点名称与角色描述微调：区分「裁决」与「调解」由状态码承载
UPDATE wf_node_def
   SET key_material = '裁决书 / 调解书、送达回证'
 WHERE flow_key = 'ARBITRATION' AND status_code = 'AWARD';

-- 5) 字典补三个状态名（前端筛选下拉使用）
INSERT INTO sys_dict (dict_type, dict_code, dict_name, sort, status)
SELECT 'case_status', 'APPEAL', '上诉', 21, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'case_status' AND dict_code = 'APPEAL');
INSERT INTO sys_dict (dict_type, dict_code, dict_name, sort, status)
SELECT 'case_status', 'RETRIAL_HEARING', '开庭（二审）', 22, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'case_status' AND dict_code = 'RETRIAL_HEARING');
INSERT INTO sys_dict (dict_type, dict_code, dict_name, sort, status)
SELECT 'case_status', 'RULING', '裁定', 23, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'case_status' AND dict_code = 'RULING');
