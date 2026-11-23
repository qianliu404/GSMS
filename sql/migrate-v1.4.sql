-- =============================================================
-- V1.4 迁移：新建案件表单精简 + 厂区自定义
-- 1) 员工工号、案件描述改为可空（表单不再强制填写）
-- 2) 新增 plant_area 字典，厂区改由字典维护，支持自定义新增
-- =============================================================

-- ---------- 1. 字段可空 ----------
ALTER TABLE case_main
  MODIFY employee_id VARCHAR(32) NULL COMMENT '员工工号',
  MODIFY description TEXT NULL COMMENT '案件描述';

-- ---------- 2. 厂区字典 ----------
INSERT INTO sys_dict (dict_type, dict_code, dict_name, sort, status) VALUES
 ('plant_area', 'HD',     '华东厂区', 1, 1),
 ('plant_area', 'HN',     '华南厂区', 2, 1),
 ('plant_area', 'XN',     '西南厂区', 3, 1),
 ('plant_area', 'HB',     '华北厂区', 4, 1),
 ('plant_area', 'HQ',     '总部基地', 5, 1)
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), sort = VALUES(sort), status = 1;

-- ---------- 3. 校验 ----------
SELECT '字段可空' AS chk, column_name, is_nullable
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'case_main'
   AND column_name IN ('employee_id', 'description');
SELECT '厂区字典' AS chk, dict_code, dict_name, sort
  FROM sys_dict WHERE dict_type = 'plant_area' ORDER BY sort;
