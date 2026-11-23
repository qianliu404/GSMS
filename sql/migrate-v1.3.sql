-- =====================================================================
-- V1.3 迁移：按交互原型补充业务字段 + 流程节点定义表
-- 执行前提：case_system 库已按 init.sql 初始化（V1.2）
-- 幂等说明：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报
--           1060/1050，可忽略；脚本末尾给出校验语句。
-- =====================================================================
USE case_system;

-- ---------- 1. 案件主表：组织归属与归档信息 ----------
ALTER TABLE case_main
  ADD COLUMN plant_area     VARCHAR(50)  DEFAULT NULL COMMENT '厂区' AFTER department_name,
  ADD COLUMN workshop_team  VARCHAR(100) DEFAULT NULL COMMENT '车间班组，如：生产一部 · 二车间甲班' AFTER plant_area,
  ADD COLUMN leader_l1      VARCHAR(50)  DEFAULT NULL COMMENT '小班长 / 直属主管' AFTER workshop_team,
  ADD COLUMN leader_l2      VARCHAR(50)  DEFAULT NULL COMMENT '大班长 / 上级主管' AFTER leader_l1,
  ADD COLUMN close_type     VARCHAR(30)  DEFAULT NULL COMMENT '结案方式：TREATMENT_SETTLED/MEDIATION/AWARD/WITHDRAW/OTHER' AFTER close_date,
  ADD COLUMN archive_pages  INT          DEFAULT NULL COMMENT '卷宗页数' AFTER archive_status,
  ADD COLUMN archive_years  INT          DEFAULT NULL COMMENT '保管期限（年）' AFTER archive_pages;

-- ---------- 2. 工伤扩展表：三项一次性待遇（状态 + 金额） ----------
-- 状态：NOT_APPLIED 未申领 / APPLYING 申领中 / RECEIVED 已到账 / INELIGIBLE 不符合条件
-- 金额与 treatment_amount 一致，保留数值明文供报表聚合，展示层按角色脱敏。
ALTER TABLE case_injury_ext
  ADD COLUMN subsidy_disability_status VARCHAR(20)   NOT NULL DEFAULT 'NOT_APPLIED' COMMENT '一次性伤残补助金申领状态' AFTER treatment_amount,
  ADD COLUMN subsidy_disability_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '一次性伤残补助金金额' AFTER subsidy_disability_status,
  ADD COLUMN subsidy_medical_status    VARCHAR(20)   NOT NULL DEFAULT 'NOT_APPLIED' COMMENT '一次性工伤医疗补助金申领状态' AFTER subsidy_disability_amount,
  ADD COLUMN subsidy_medical_amount    DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '一次性工伤医疗补助金金额' AFTER subsidy_medical_status,
  ADD COLUMN subsidy_employment_status VARCHAR(20)   NOT NULL DEFAULT 'NOT_APPLIED' COMMENT '一次性伤残就业补助金申领状态' AFTER subsidy_medical_amount,
  ADD COLUMN subsidy_employment_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '一次性伤残就业补助金金额' AFTER subsidy_employment_status;

-- ---------- 3. 流程节点定义 ----------
-- 状态码仍由 CaseStateMachine 约束（流转合法性），本表只承载节点展示与办理要素，
-- 因此调整顺序 / 时限 / 角色不会破坏状态机。
CREATE TABLE IF NOT EXISTS wf_node_def (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  flow_key      VARCHAR(20)  NOT NULL                COMMENT '流程：INJURY/ARBITRATION',
  status_code   VARCHAR(30)  NOT NULL                COMMENT '对应状态机节点码',
  node_name     VARCHAR(50)  NOT NULL                COMMENT '节点名称',
  sort_no       INT          NOT NULL                COMMENT '展示顺序，从 1 开始',
  time_limit    VARCHAR(20)  DEFAULT NULL            COMMENT '办理时限，如 10 天',
  main_role     VARCHAR(100) DEFAULT NULL            COMMENT '主责角色',
  key_material  VARCHAR(300) DEFAULT NULL            COMMENT '关键材料 / 输出文书',
  sys_action    VARCHAR(300) DEFAULT NULL            COMMENT '系统动作',
  allow_rollback TINYINT     NOT NULL DEFAULT 1      COMMENT '是否允许退回上一节点 0否/1是',
  enabled       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用 0停用/1启用',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_status (flow_key, status_code),
  KEY idx_flow_sort (flow_key, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点定义';

INSERT INTO wf_node_def (flow_key, status_code, node_name, sort_no, time_limit, main_role, key_material, sys_action, allow_rollback) VALUES
('INJURY','DRAFT',        '草稿',     1,  '—',     '员工 / HRBP',        '事故情况说明、初诊病历',        '草稿态可编辑删除，不占案号',       0),
('INJURY','FILED',        '立案',     2,  '3 天',  '法务专员',           '工伤事故报告、劳动关系证明',    '生成案号，启动节点计时',           1),
('INJURY','INVESTIGATION','调查取证', 3,  '10 天', '法务专员 / 业务部门','现场照片、证人证言、监控、考勤','证据清单、任务分派与催办',         1),
('INJURY','DECLARE',      '申报',     4,  '30 天', '法务专员',           '工伤认定申请表、医疗诊断证明',  '人社局申报登记、受理号回填',       1),
('INJURY','RECOGNITION',  '认定',     5,  '60 天', '法务专员',           '工伤认定决定书',                '回填结论；不予认定转争议处理',     1),
('INJURY','APPRAISAL',    '鉴定',     6,  '30 天', '法务专员',           '劳动能力鉴定结论书',            '回填伤残等级，驱动待遇测算',       1),
('INJURY','TREATMENT',    '待遇处理', 7,  '15 天', '薪酬专员 / 财务',    '待遇核定表、三项一次性待遇申领','待遇测算与发放流程联动',           1),
('INJURY','DISPUTE',      '争议处理', 8,  '视情况', '法务 / 外部律师',    '复议、诉讼材料',                '生成关联子案件，主案暂停计时',     1),
('INJURY','CLOSED',       '结案',     9,  '5 天',  '法务主管',           '结案报告',                      '结案审批，金额与责任认定归档',     1),
('INJURY','ARCHIVED',     '归档',     10, '3 天',  '档案管理员',         '卷宗目录、电子档案',            '只读锁定，进入档案库',             1),
('ARBITRATION','DRAFT',   '草稿',     1,  '—',     '员工 / HRBP',        '争议事实与诉求',                '草稿态可编辑删除',                 0),
('ARBITRATION','FILED',   '立案',     2,  '5 天',  '法务专员',           '仲裁申请书、证据目录',          '生成案号，法定时限计时启动',       1),
('ARBITRATION','EVIDENCE','证据准备', 3,  '10 天', '法务专员 / 业务部门','考勤、工资、规章制度、送达凭证','举证期限提醒、证据编号与质证意见', 1),
('ARBITRATION','HEARING', '开庭',     4,  '15 天', '法务 / 代理律师',    '答辩状、庭审笔录',              '开庭排期、出庭提醒、庭审纪要',     1),
('ARBITRATION','AWARD',   '裁决/调解',5,  '45 天', '法务主管',           '裁决书 / 调解协议',            '回填结果与金额，触发上诉判断',     1),
('ARBITRATION','PERFORM', '履行',     6,  '15 天', '薪酬 / 财务',        '付款凭证、履行确认单',          '付款流程联动，逾期自动预警',       1),
('ARBITRATION','CLOSED',  '结案',     7,  '5 天',  '法务主管',           '结案报告',                      '结案审批，复盘与改进建议',         1),
('ARBITRATION','ARCHIVED','归档',     8,  '3 天',  '档案管理员',         '卷宗目录',                      '只读锁定，进入档案库',             1);

-- ---------- 4. 菜单调整（原型三组结构） ----------
-- 新增 5 个页面；「流程管理」更名为「流程节点配置」复用原菜单项；
-- 「案件新建」改为顶栏抽屉，菜单项停用。
INSERT INTO sys_menu (menu_code, menu_name, menu_type, parent_id, path, perm_code, icon, sort) VALUES
('DASHBOARD',       '流程总览',  'M', 0, '/dashboard',              'dashboard:view', 'DataBoard',   5),
('CASE_INJURY',     '工伤案件',  'M', 0, '/cases?type=INJURY',      'case:injury',    'FirstAidKit', 21),
('CASE_ARBITRATION','劳动仲裁',  'M', 0, '/cases?type=ARBITRATION', 'case:arb',       'Scale',       22),
('INJURY_LEDGER',   '工伤台账',  'M', 0, '/ledger',                 'case:ledger',    'Notebook',    23),
('ARCHIVE_LIB',     '归档库',    'M', 0, '/archive',                'case:archive',   'Files',       45)
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), path=VALUES(path), icon=VALUES(icon), sort=VALUES(sort);

UPDATE sys_menu SET menu_name='流程节点配置', icon='Share', sort=51 WHERE menu_code='P-SYS-02';
UPDATE sys_menu SET status=0 WHERE menu_code='P-CASE-02';
UPDATE sys_menu SET sort=30 WHERE menu_code='P-TODO-01';
UPDATE sys_menu SET sort=35 WHERE menu_code='P-DEADLINE-01';
UPDATE sys_menu SET sort=40 WHERE menu_code='P-REPORT-01';
UPDATE sys_menu SET sort=60 WHERE menu_code='P-SYS-01';
UPDATE sys_menu SET sort=65 WHERE menu_code='P-SYS-03';
UPDATE sys_menu SET sort=70 WHERE menu_code='P-MOBILE-01';

-- 系统管理员与案件管理员默认可访问新增菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM sys_role r CROSS JOIN sys_menu m
 WHERE r.role_code IN ('ROLE_SYS_ADMIN','ROLE_CASE_ADMIN')
   AND m.menu_code IN ('DASHBOARD','CASE_INJURY','CASE_ARBITRATION','INJURY_LEDGER','ARCHIVE_LIB')
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);

-- ---------- 5. 字典补充 ----------
INSERT INTO sys_dict (dict_type, dict_code, dict_name, sort, status) VALUES
('subsidy_status','NOT_APPLIED','未申领',1,1),
('subsidy_status','APPLYING','申领中',2,1),
('subsidy_status','RECEIVED','已到账',3,1),
('subsidy_status','INELIGIBLE','不符合条件',4,1),
('close_type','TREATMENT_SETTLED','待遇结清',1,1),
('close_type','MEDIATION','调解结案',2,1),
('close_type','AWARD','裁决结案',3,1),
('close_type','WITHDRAW','撤诉',4,1),
('close_type','OTHER','其他',5,1)
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), sort=VALUES(sort);

-- ---------- 6. 存量数据回填 ----------
-- 已结案的工伤案件按常见口径补一组示例金额，便于台账/详情页展示（可按实际调整）
UPDATE case_injury_ext e
  JOIN case_main c ON c.id = e.case_id
   SET e.subsidy_disability_amount = 52000, e.subsidy_medical_amount = 18000, e.subsidy_employment_amount = 26000
 WHERE c.case_type = 'INJURY' AND c.status IN ('CLOSED','ARCHIVED')
   AND e.subsidy_disability_amount = 0;

-- ---------- 校验 ----------
SELECT 'case_main 新增列' AS chk, COUNT(*) AS cnt FROM information_schema.columns
 WHERE table_schema='case_system' AND table_name='case_main' AND column_name IN ('plant_area','workshop_team','leader_l1','leader_l2','close_type','archive_pages','archive_years');
SELECT 'case_injury_ext 新增列' AS chk, COUNT(*) AS cnt FROM information_schema.columns
 WHERE table_schema='case_system' AND table_name='case_injury_ext' AND column_name LIKE 'subsidy_%';
SELECT 'wf_node_def 节点数' AS chk, flow_key, COUNT(*) AS cnt FROM wf_node_def GROUP BY flow_key;
SELECT '新增菜单' AS chk, menu_code, menu_name, path, sort FROM sys_menu WHERE menu_code IN ('DASHBOARD','CASE_INJURY','CASE_ARBITRATION','INJURY_LEDGER','ARCHIVE_LIB') OR menu_code IN ('P-SYS-02','P-CASE-02') ORDER BY sort;
