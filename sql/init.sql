-- =============================================================
-- 工伤与劳动仲裁案件协同管理系统  初始化脚本
-- 目标实例：MySQL 8.0.45 / utf8mb4 / utf8mb4_0900_ai_ci
-- 执行方式：mysql -u root -p --default-character-set=utf8mb4 < init.sql
-- 说明：本脚本可重复执行（DROP TABLE IF EXISTS），仅用于初始化空库
-- =============================================================

CREATE DATABASE IF NOT EXISTS case_system
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE case_system;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------
-- 一、案件域
-- -------------------------------------------------------------

DROP TABLE IF EXISTS case_main;
CREATE TABLE case_main (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_no         VARCHAR(32)  NOT NULL                COMMENT '案件编号：GY/ZS/GL+年月+流水',
  case_type       VARCHAR(20)  NOT NULL                COMMENT 'INJURY工伤/ARBITRATION仲裁/RELATED关联争议',
  case_name       VARCHAR(200) NOT NULL                COMMENT '案件名称',
  employee_id     VARCHAR(32)  DEFAULT NULL            COMMENT '员工工号',
  employee_name   VARCHAR(50)  NOT NULL                COMMENT '员工姓名（敏感）',
  id_card_no      VARCHAR(64)  DEFAULT NULL            COMMENT '身份证号（AES加密存储，敏感）',
  department_id   VARCHAR(32)  NOT NULL                COMMENT '部门ID',
  department_name VARCHAR(100) DEFAULT NULL            COMMENT '部门名称（冗余展示）',
  plant_area      VARCHAR(50)  DEFAULT NULL            COMMENT '厂区',
  workshop_team   VARCHAR(100) DEFAULT NULL            COMMENT '车间班组，如：生产一部 · 二车间甲班',
  leader_l1       VARCHAR(50)  DEFAULT NULL            COMMENT '小班长 / 直属主管',
  leader_l2       VARCHAR(50)  DEFAULT NULL            COMMENT '大班长 / 上级主管',
  occur_date      DATE         NOT NULL                COMMENT '发生日期',
  description     TEXT         DEFAULT NULL            COMMENT '案件描述',
  status          VARCHAR(30)  NOT NULL DEFAULT 'DRAFT' COMMENT '当前状态（按案件类型各自状态机）',
  owner_id        VARCHAR(32)  NOT NULL                COMMENT '案件负责人',
  handler_id      VARCHAR(32)  NOT NULL                COMMENT '承办人',
  close_date      DATE         DEFAULT NULL            COMMENT '结案日期',
  close_type      VARCHAR(30)  DEFAULT NULL            COMMENT '结案方式：TREATMENT_SETTLED/MEDIATION/AWARD/WITHDRAW/OTHER',
  archive_status  TINYINT      NOT NULL DEFAULT 0      COMMENT '0未归档 1已归档',
  archive_pages   INT          DEFAULT NULL            COMMENT '卷宗页数',
  archive_years   INT          DEFAULT NULL            COMMENT '保管期限（年）',
  create_by       VARCHAR(32)  NOT NULL                COMMENT '创建人',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_case_no (case_no),
  KEY idx_case_type_status (case_type, status),
  KEY idx_employee_id (employee_id),
  KEY idx_department_id (department_id),
  KEY idx_create_time (create_time),
  KEY idx_update_time (update_time),
  KEY idx_occur_date (occur_date),
  KEY idx_employee_name (employee_name),
  KEY idx_handler (handler_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件主表';

DROP TABLE IF EXISTS case_injury_ext;
CREATE TABLE case_injury_ext (
  case_id          BIGINT        NOT NULL COMMENT '案件ID',
  injury_date      DATE          NOT NULL COMMENT '伤害发生日期',
  injury_place     VARCHAR(200)  NOT NULL COMMENT '伤害地点',
  injury_reason    VARCHAR(500)  NOT NULL COMMENT '伤害原因',
  apply_date       DATE          DEFAULT NULL COMMENT '申报日期',
  recognize_date   DATE          DEFAULT NULL COMMENT '认定日期',
  recognize_result VARCHAR(50)   DEFAULT NULL COMMENT 'RECOGNIZED/NOT_RECOGNIZED',
  appraisal_date   DATE          DEFAULT NULL COMMENT '鉴定日期',
  appraisal_level  VARCHAR(20)   DEFAULT NULL COMMENT '伤残等级：一至十级/无级别',
  rest_days        DECIMAL(6,1)  DEFAULT NULL COMMENT '鉴定休假天数（天），允许小数',
  treatment_amount DECIMAL(14,2) DEFAULT NULL COMMENT '待遇金额（AES加密，敏感）',
  subsidy_disability_status VARCHAR(20)   NOT NULL DEFAULT 'NOT_APPLIED' COMMENT '一次性伤残补助金申领状态',
  subsidy_disability_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '一次性伤残补助金金额',
  subsidy_medical_status    VARCHAR(20)   NOT NULL DEFAULT 'NOT_APPLIED' COMMENT '一次性工伤医疗补助金申领状态',
  subsidy_medical_amount    DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '一次性工伤医疗补助金金额',
  subsidy_employment_status VARCHAR(20)   NOT NULL DEFAULT 'NOT_APPLIED' COMMENT '一次性伤残就业补助金申领状态',
  subsidy_employment_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '一次性伤残就业补助金金额',
  dispute_flag     TINYINT       DEFAULT 0 COMMENT '是否争议 0否/1是',
  PRIMARY KEY (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工伤扩展表';

DROP TABLE IF EXISTS case_arbitration_ext;
CREATE TABLE case_arbitration_ext (
  case_id        BIGINT        NOT NULL COMMENT '案件ID',
  arbitration_no VARCHAR(64)   DEFAULT NULL COMMENT '仲裁案号',
  apply_date     DATE          NOT NULL COMMENT '申请日期',
  accept_date    DATE          DEFAULT NULL COMMENT '受理日期',
  hearing_date   DATETIME      DEFAULT NULL COMMENT '开庭时间',
  result_type    VARCHAR(20)   DEFAULT NULL COMMENT 'AWARD裁决/MEDIATION调解/WITHDRAW撤诉',
  award_amount   DECIMAL(14,2) DEFAULT NULL COMMENT '裁决调解金额（AES加密，敏感）',
  perform_amount DECIMAL(14,2) DEFAULT NULL COMMENT '履行款金额',
  perform_status VARCHAR(20)   DEFAULT NULL COMMENT 'PENDING/PARTIAL/DONE',
  perform_date   DATE          DEFAULT NULL COMMENT '履行完成日期',
  PRIMARY KEY (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仲裁扩展表';

DROP TABLE IF EXISTS case_member;
CREATE TABLE case_member (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id     BIGINT      NOT NULL COMMENT '案件ID',
  user_id     VARCHAR(32) NOT NULL COMMENT '用户ID',
  member_role VARCHAR(30) NOT NULL COMMENT 'OWNER/HANDLER/CO_HANDLER/COUNTERSIGN/READER',
  join_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_case_user_role (case_id, user_id, member_role),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件成员表（数据权限依据）';

DROP TABLE IF EXISTS case_status_log;
CREATE TABLE case_status_log (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id      BIGINT       NOT NULL COMMENT '案件ID',
  from_status  VARCHAR(30)  NOT NULL COMMENT '原状态',
  to_status    VARCHAR(30)  NOT NULL COMMENT '新状态',
  action       VARCHAR(50)  NOT NULL COMMENT '操作动作',
  operator_id  VARCHAR(32)  NOT NULL COMMENT '操作人',
  opinion      VARCHAR(500) DEFAULT NULL COMMENT '意见',
  operate_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_case_id (case_id),
  KEY idx_operate_time (operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件状态流转表（不可物理删除）';

DROP TABLE IF EXISTS case_relation;
CREATE TABLE case_relation (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id         BIGINT       NOT NULL COMMENT '主案件ID',
  related_case_id BIGINT       NOT NULL COMMENT '被关联案件ID',
  relation_type   VARCHAR(30)  NOT NULL COMMENT 'RELATED/ARBITRATION/INJURY',
  relation_desc   VARCHAR(500) DEFAULT NULL COMMENT '关联说明',
  create_by       VARCHAR(32)  NOT NULL COMMENT '创建人',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_case_related (case_id, related_case_id, relation_type),
  KEY idx_related_case (related_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件关联表';

DROP TABLE IF EXISTS wf_node_def;
CREATE TABLE wf_node_def (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  flow_key       VARCHAR(20)  NOT NULL                COMMENT '流程：INJURY/ARBITRATION',
  status_code    VARCHAR(30)  NOT NULL                COMMENT '对应状态机节点码',
  node_name      VARCHAR(50)  NOT NULL                COMMENT '节点名称',
  sort_no        INT          NOT NULL                COMMENT '展示顺序，从 1 开始',
  time_limit     VARCHAR(20)  DEFAULT NULL            COMMENT '办理时限，如 10 天',
  main_role      VARCHAR(100) DEFAULT NULL            COMMENT '主责角色',
  key_material   VARCHAR(300) DEFAULT NULL            COMMENT '关键材料 / 输出文书',
  sys_action     VARCHAR(300) DEFAULT NULL            COMMENT '系统动作',
  allow_rollback TINYINT      NOT NULL DEFAULT 1      COMMENT '是否允许退回上一节点 0否/1是',
  enabled        TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用 0停用/1启用',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_status (flow_key, status_code),
  KEY idx_flow_sort (flow_key, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点定义';

-- -------------------------------------------------------------
-- 二、协同与文档域
-- -------------------------------------------------------------

DROP TABLE IF EXISTS case_document;
CREATE TABLE case_document (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id        BIGINT       NOT NULL COMMENT '案件ID',
  doc_name       VARCHAR(200) NOT NULL COMMENT '文档名称',
  doc_type       VARCHAR(50)  NOT NULL COMMENT 'EVIDENCE/TEMPLATE_DOC/OTHER',
  file_path      VARCHAR(500) NOT NULL COMMENT '相对路径 /files/2026/11/xxx.pdf',
  file_size      BIGINT       NOT NULL COMMENT '文件大小(byte)',
  file_hash      VARCHAR(64)  DEFAULT NULL COMMENT 'SHA-256，用于完整性校验',
  version        INT          NOT NULL DEFAULT 1 COMMENT '版本号',
  parent_id      BIGINT       DEFAULT NULL COMMENT '父文档ID（版本链）',
  upload_by      VARCHAR(32)  NOT NULL COMMENT '上传人',
  upload_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  watermark      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否加水印',
  sensitive_flag TINYINT      NOT NULL DEFAULT 0 COMMENT '是否敏感文档',
  deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_case_id (case_id),
  KEY idx_doc_type (doc_type),
  KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件文档表';

DROP TABLE IF EXISTS deadline_reminder;
CREATE TABLE deadline_reminder (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id           BIGINT       NOT NULL COMMENT '案件ID',
  node_code         VARCHAR(50)  NOT NULL COMMENT '节点编码',
  node_name         VARCHAR(100) NOT NULL COMMENT '节点名称',
  deadline_type     VARCHAR(20)  NOT NULL COMMENT 'LEGAL法定/INTERNAL内部',
  deadline_date     DATE         NOT NULL COMMENT '截止日期',
  manual_date       DATE         DEFAULT NULL COMMENT '人工调整后的截止日期，为空表示按节点时限自动推算',
  handle_date       DATE         DEFAULT NULL COMMENT '本节点办理日期（默认当天，可修改），截止日期据此推算',
  start_time        DATETIME     DEFAULT NULL COMMENT '节点开始计时时间（点击提交开启），为空表示未开启',
  remind_days       INT          NOT NULL DEFAULT 3 COMMENT '提前提醒天数',
  status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REMINDED/OVERDUE/DONE',
  handler_id        VARCHAR(32)  NOT NULL COMMENT '责任人',
  overdue_level     INT          NOT NULL DEFAULT 0 COMMENT '超期升级级别',
  overdue_reason    VARCHAR(500) DEFAULT NULL COMMENT '超期原因',
  supervise_opinion VARCHAR(500) DEFAULT NULL COMMENT '督办意见',
  create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_case_id (case_id),
  KEY idx_deadline_status (deadline_date, status),
  KEY idx_handler (handler_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='期限提醒表';

DROP TABLE IF EXISTS task;
CREATE TABLE task (
  id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id     BIGINT        NOT NULL COMMENT '案件ID',
  task_type   VARCHAR(50)   NOT NULL COMMENT '任务类型',
  assigner_id VARCHAR(32)   NOT NULL COMMENT '分派人',
  assignee_id VARCHAR(32)   NOT NULL COMMENT '责任人',
  deadline    DATETIME      NOT NULL COMMENT '截止时间',
  priority    VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
  status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/OVERDUE',
  finish_time DATETIME      DEFAULT NULL COMMENT '完成时间',
  result      VARCHAR(1000) DEFAULT NULL COMMENT '完成结果',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_case_id (case_id),
  KEY idx_assignee_status (assignee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协同任务表';

DROP TABLE IF EXISTS countersign;
CREATE TABLE countersign (
  id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id          BIGINT        NOT NULL COMMENT '案件ID',
  node_code        VARCHAR(50)   NOT NULL COMMENT '会签节点',
  countersigner_id VARCHAR(32)   NOT NULL COMMENT '会签人',
  opinion_type     VARCHAR(20)   NOT NULL COMMENT 'AGREE/DISAGREE/CONDITIONAL',
  opinion          VARCHAR(1000) NOT NULL COMMENT '会签意见',
  countersign_time DATETIME      DEFAULT NULL COMMENT '会签时间',
  result           VARCHAR(20)   DEFAULT 'PENDING' COMMENT 'PASS/REJECT/PENDING',
  create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_case_node (case_id, node_code),
  KEY idx_countersigner (countersigner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会签记录表';

DROP TABLE IF EXISTS notification;
CREATE TABLE notification (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  receiver_id   VARCHAR(32)  NOT NULL COMMENT '接收人',
  channel       VARCHAR(20)  NOT NULL COMMENT 'INNER/EMAIL/WECOM/DINGTALK/FEISHU',
  template_code VARCHAR(50)  NOT NULL COMMENT '模板编码',
  title         VARCHAR(200) DEFAULT NULL COMMENT '标题',
  content       VARCHAR(1000) DEFAULT NULL COMMENT '内容',
  biz_id        VARCHAR(64)  DEFAULT NULL COMMENT '业务ID',
  send_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAIL',
  send_time     DATETIME     DEFAULT NULL COMMENT '发送时间',
  retry_count   INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
  read_flag     TINYINT      NOT NULL DEFAULT 0 COMMENT '站内信已读标记',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_receiver_status (receiver_id, send_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知发送记录表';

DROP TABLE IF EXISTS wf_instance;
CREATE TABLE wf_instance (
  id                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id             BIGINT      NOT NULL COMMENT '案件ID',
  process_def_id      VARCHAR(64) NOT NULL COMMENT '流程定义ID',
  process_instance_id VARCHAR(64) NOT NULL COMMENT '流程实例ID',
  business_key        VARCHAR(64) NOT NULL COMMENT '业务键',
  status              VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/COMPLETED/TERMINATED',
  start_time          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  end_time            DATETIME    DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (id),
  KEY idx_case_id (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程实例表';

DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id         VARCHAR(32)  NOT NULL COMMENT '用户ID',
  user_name       VARCHAR(50)  NOT NULL COMMENT '用户名',
  operation       VARCHAR(100) NOT NULL COMMENT '操作类型',
  module          VARCHAR(50)  NOT NULL COMMENT '模块',
  business_id     VARCHAR(64)  DEFAULT NULL COMMENT '业务ID',
  request_ip      VARCHAR(64)  DEFAULT NULL COMMENT 'IP',
  request_params  TEXT         DEFAULT NULL COMMENT '请求参数（脱敏）',
  result          VARCHAR(20)  NOT NULL COMMENT 'SUCCESS/FAIL',
  trace_id        VARCHAR(64)  DEFAULT NULL COMMENT '链路ID',
  before_data     TEXT         DEFAULT NULL COMMENT '变更前（脱敏）',
  after_data      TEXT         DEFAULT NULL COMMENT '变更后（脱敏）',
  operate_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_operate_time (operate_time),
  KEY idx_module_business (module, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志表（保存>=3年，不可物理删除）';

-- -------------------------------------------------------------
-- 三、系统域
-- -------------------------------------------------------------

DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  dept_code  VARCHAR(64)  NOT NULL COMMENT '部门编码',
  dept_name  VARCHAR(100) NOT NULL COMMENT '部门名称',
  parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID，0为根',
  sort       INT          NOT NULL DEFAULT 0 COMMENT '排序',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dept_code (dept_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表';

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       VARCHAR(32)  NOT NULL COMMENT '登录名',
  user_name     VARCHAR(50)  NOT NULL COMMENT '姓名',
  dept_id       BIGINT       DEFAULT NULL COMMENT '部门ID',
  mobile        VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  email         VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  position      VARCHAR(50)  DEFAULT NULL COMMENT '岗位',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 加盐哈希',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  last_login_time DATETIME   DEFAULT NULL COMMENT '最后登录时间',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_id (user_id),
  KEY idx_dept_id (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_code  VARCHAR(50) NOT NULL COMMENT '角色编码',
  role_name  VARCHAR(50) NOT NULL COMMENT '角色名称',
  data_scope VARCHAR(20) NOT NULL DEFAULT 'SELF' COMMENT 'ALL全部/DEPT本部门/SELF本人',
  status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  remark     VARCHAR(200) DEFAULT NULL COMMENT '备注',
  create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  id      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id VARCHAR(32) NOT NULL COMMENT '用户ID',
  role_id BIGINT      NOT NULL COMMENT '角色ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色表';

DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
  id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  menu_code VARCHAR(50)  NOT NULL COMMENT '菜单编码（对应页面编号）',
  menu_name VARCHAR(50)  NOT NULL COMMENT '菜单名称',
  menu_type VARCHAR(10)  NOT NULL DEFAULT 'M' COMMENT 'M菜单/B按钮',
  parent_id BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID',
  path      VARCHAR(200) DEFAULT NULL COMMENT '路由',
  perm_code VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
  icon      VARCHAR(50)  DEFAULT NULL COMMENT '图标',
  sort      INT          NOT NULL DEFAULT 0 COMMENT '排序',
  status    TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  PRIMARY KEY (id),
  UNIQUE KEY uk_menu_code (menu_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单权限表';

DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
  id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  menu_id BIGINT NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单表';

DROP TABLE IF EXISTS sys_dict;
CREATE TABLE sys_dict (
  id        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  dict_type VARCHAR(50) NOT NULL COMMENT '字典类型',
  dict_code VARCHAR(50) NOT NULL COMMENT '字典编码',
  dict_name VARCHAR(100) NOT NULL COMMENT '字典名称',
  sort      INT         NOT NULL DEFAULT 0 COMMENT '排序',
  status    TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_code (dict_type, dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据字典表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- 初始化数据
-- =============================================================

-- 部门
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort) VALUES
 (1, 'HQ',     '总公司',       0, 1),
 (2, 'HR',     '人力资源部',   1, 1),
 (3, 'LEGAL',  '法务部',       1, 2),
 (4, 'SAFETY', '安全环保部',   1, 3),
 (5, 'FIN',    '财务部',       1, 4);

-- 角色（8 个，对应页面清单第二节）
INSERT INTO sys_role (id, role_code, role_name, data_scope, remark) VALUES
 (1, 'ROLE_SYS_ADMIN',   '系统管理员', 'ALL',  '系统配置、流程管理、审计日志、全部案件'),
 (2, 'ROLE_CASE_ADMIN',  '案件管理员', 'ALL',  '全部案件办理、结案归档、报表'),
 (3, 'ROLE_DEPT_HEAD',   '部门负责人', 'DEPT', '本部门案件、审批、督办、报表'),
 (4, 'ROLE_HANDLER',     '承办人',     'SELF', '本人承办案件全流程办理'),
 (5, 'ROLE_CO_HANDLER',  '协办人',     'SELF', '本人协办案件的协同操作'),
 (6, 'ROLE_COUNTERSIGN', '会签人',     'SELF', '会签意见'),
 (7, 'ROLE_READER',      '阅知人',     'SELF', '案件只读与受控下载'),
 (8, 'ROLE_AUDITOR',     '审计员',     'ALL',  '审计日志查询导出（不看业务敏感字段）');

-- 菜单（原型三组结构 + 页面清单）
INSERT INTO sys_menu (id, menu_code, menu_name, menu_type, parent_id, path, perm_code, icon, sort) VALUES
 (1,  'P-CASE-01',     '案件列表',   'M', 0,  '/cases',                'case:list',      'Document',  10),
 (2,  'P-CASE-02',     '案件新建',   'M', 0,  '/cases/create',         'case:create',    'Plus',      11),
 (3,  'P-TODO-01',     '我的待办',   'M', 0,  '/todos',                'todo:list',      'Bell',      30),
 (4,  'P-DEADLINE-01', '期限中心',   'M', 0,  '/deadlines',            'deadline:list',  'AlarmClock',35),
 (5,  'P-REPORT-01',   '报表中心',   'M', 0,  '/reports',              'report:view',    'DataLine',  40),
 (6,  'P-SYS-01',      '用户与角色', 'M', 0,  '/system/users',         'sys:user:list',  'User',      60),
 (7,  'P-SYS-02',      '流程节点配置','M',0,  '/system/workflow',      'sys:wf:list',    'Share',     51),
 (8,  'P-SYS-03',      '审计日志',   'M', 0,  '/system/audit-logs',    'sys:audit:list', 'Tickets',   65),
 (9,  'P-MOBILE-01',   '移动待办',   'M', 0,  '/m/todos',              'todo:mobile',    'Iphone',    70),
 (10, 'P-CASE-03',     '案件详情',   'M', 0,  '/cases/:id',            'case:detail',    null,        12),
 (11, 'P-CASE-04',     '案件关联',   'M', 0,  '/cases/:id/relations',  'case:relation',  null,        13),
 (12, 'P-CASE-05',     '结案归档',   'M', 0,  '/cases/:id/close',      'case:close',     null,        14),
 (13, 'P-TASK-01',     '任务分派',   'M', 0,  '/cases/:id/tasks',      'task:assign',    null,        15),
 (14, 'P-DOC-01',      '文档中心',   'M', 0,  '/cases/:id/documents',  'doc:list',       null,        16),
 (15, 'DASHBOARD',     '流程总览',   'M', 0,  '/dashboard',            'dashboard:view', 'DataBoard',   5),
 (16, 'CASE_INJURY',   '工伤案件',   'M', 0,  '/cases?type=INJURY',    'case:injury',    'FirstAidKit',21),
 (17, 'CASE_ARBITRATION','劳动仲裁', 'M', 0,  '/cases?type=ARBITRATION','case:arb',     'Scale',      22),
 (18, 'INJURY_LEDGER', '台账',       'M', 0,  '/ledger',               'case:ledger',    'Notebook',   23),
 (21, 'CASE_RELATION', '争议关联',   'M', 0,  '/relations',            'case:relation:view','Link',      24),
 (19, 'ARCHIVE_LIB',   '归档库',     'M', 0,  '/archive',              'case:archive',   'Files',      45),
 (20, 'RECYCLE_BIN',   '回收站',     'M', 0,  '/recycle-bin',          'sys:recycle:list','Delete',     64);

-- 案件新建改为顶栏抽屉，菜单项停用（保留记录，便于回滚）
UPDATE sys_menu SET status = 0 WHERE menu_code = 'P-CASE-02';

-- 角色菜单：管理员全量，其余按职责
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
 (2,1),(2,3),(2,4),(2,5),(2,10),(2,11),(2,12),(2,13),(2,14),(2,15),(2,16),(2,17),(2,18),(2,19),(2,20),(2,21),
 (3,1),(3,3),(3,4),(3,5),(3,10),(3,11),(3,14),(3,15),(3,18),(3,19),(3,21),
 (4,1),(4,3),(4,4),(4,5),(4,10),(4,11),(4,12),(4,13),(4,14),(4,9),(4,15),(4,16),(4,17),(4,18),(4,21),
 (5,1),(5,3),(5,10),(5,11),(5,13),(5,14),(5,9),(5,15),(5,21),
 (6,1),(6,3),(6,10),(6,14),(6,9),(6,15),(6,21),
 (7,1),(7,10),(7,14),(7,15),(7,21),
 (8,8);

-- 字典
INSERT INTO sys_dict (dict_type, dict_code, dict_name, sort) VALUES
 ('case_type','INJURY','工伤案件',1),
 ('case_type','ARBITRATION','劳动仲裁案件',2),
 ('case_type','RELATED','关联争议案件',3),

 ('case_status','DRAFT','草稿',1),
 ('case_status','FILED','立案',2),
 ('case_status','INVESTIGATION','调查取证',3),
 ('case_status','DECLARE','申报',4),
 ('case_status','RECOGNITION','认定',5),
 ('case_status','APPRAISAL','鉴定',6),
 ('case_status','TREATMENT','待遇处理',7),
 ('case_status','DISPUTE','争议处理',8),
 ('case_status','EVIDENCE','证据准备',9),
 ('case_status','HEARING','开庭',10),
 ('case_status','AWARD','裁决',11),
 ('case_status','MEDIATION','调解',12),
 ('case_status','PERFORM','履行',13),
 ('case_status','CLOSED','结案',14),
 ('case_status','ARCHIVED','归档',15),

 ('member_role','OWNER','负责人',1),
 ('member_role','HANDLER','承办人',2),
 ('member_role','CO_HANDLER','协办人',3),
 ('member_role','COUNTERSIGN','会签人',4),
 ('member_role','READER','阅知人',5),

 ('doc_type','EVIDENCE','证据材料',1),
 ('doc_type','TEMPLATE_DOC','生成文书',2),
 ('doc_type','OTHER','其他',3),

 ('deadline_type','LEGAL','法定时限',1),
 ('deadline_type','INTERNAL','内部时限',2),
 ('deadline_status','PENDING','待提醒',1),
 ('deadline_status','REMINDED','已提醒',2),
 ('deadline_status','OVERDUE','已超期',3),
 ('deadline_status','DONE','已完成',4),

 ('task_status','PENDING','待处理',1),
 ('task_status','DONE','已完成',2),
 ('task_status','OVERDUE','已超期',3),

 ('priority','HIGH','高',1),
 ('priority','MEDIUM','中',2),
 ('priority','LOW','低',3),

 ('countersign_opinion','AGREE','同意',1),
 ('countersign_opinion','DISAGREE','不同意',2),
 ('countersign_opinion','CONDITIONAL','有条件同意',3),

 ('plant_area','HD','华东厂区',1),
 ('plant_area','HN','华南厂区',2),
 ('plant_area','XN','西南厂区',3),
 ('plant_area','HB','华北厂区',4),
 ('plant_area','HQ','总部基地',5),
 ('notify_channel','INNER','站内信',1),
 ('notify_channel','EMAIL','邮件',2),
 ('notify_channel','WECOM','企业微信',3),
 ('notify_channel','DINGTALK','钉钉',4),
 ('notify_channel','FEISHU','飞书',5),

 ('subsidy_status','NOT_APPLIED','未申领',1),
 ('subsidy_status','APPLYING','申领中',2),
 ('subsidy_status','RECEIVED','已到账',3),
 ('subsidy_status','INELIGIBLE','不符合条件',4),

 ('close_type','TREATMENT_SETTLED','待遇结清',1),
 ('close_type','MEDIATION','调解结案',2),
 ('close_type','AWARD','裁决结案',3),
 ('close_type','WITHDRAW','撤诉',4),
 ('close_type','OTHER','其他',5);

-- 流程节点定义：状态码仍由 CaseStateMachine 约束，本表只承载展示与办理要素
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

-- 用户：初始密码由部署环境设置（BCrypt cost=10），上线后必须强制修改
INSERT INTO sys_user (id, user_id, user_name, dept_id, mobile, email, position, password_hash, status) VALUES
 (1, 'admin',     '系统管理员', 1, '13800000001', 'admin@gsms.local',     '系统管理员', '$2a$10$BRyk2j1D6A5aLDsufbialukZE8Cz9441V/d1BIjFk1pzF3QUvXXeO', 1),
 (2, 'caseadmin', '陈案管',     2, '13800000002', 'caseadmin@gsms.local', '案件管理员', '$2a$10$O2Qh9X1DrJQiAs3QCjjKaOgCzmXMJy4Uq9x4FLsj./1SeOMkBQZqi', 1),
 (3, 'depthead',  '李部长',     2, '13800000003', 'depthead@gsms.local',  '人力资源部部长', '$2a$10$hq70pW2PSUpZQSazvG/EIucRoLYOr2fT0F7.Mj.o95Baf/ajdh5LS', 1),
 (4, 'handler',   '王承办',     2, '13800000004', 'handler@gsms.local',   '工伤专员', '$2a$10$ipF1.JEnLkGpnBBHkT4xQ.BgXokNSehPNy7gvKf6usOy4aDsUqCua', 1),
 (5, 'cohandler', '赵协办',     3, '13800000005', 'cohandler@gsms.local', '法务专员', '$2a$10$d0gpzVUurGMLnINv8PvbOeWPCqHCFWmu/77JbTBrdSP.dK6Su1kxa', 1),
 (6, 'signer',    '孙会签',     2, '13800000006', 'signer@gsms.local',    '薪酬主管', '$2a$10$zBNZwO16AS6jBC1iIGUXiOIkc8yHUQDqh3xVe4dD2P/oHVrj26OBS', 1),
 (7, 'reader',    '周阅知',     5, '13800000007', 'reader@gsms.local',    '财务专员', '$2a$10$gLUJM5bD6gPjdhouKFLvBehjc2AfdDoOw1eLTGZLpFnWXxM5rb.S.', 1),
 (8, 'auditor',   '吴审计',     3, '13800000008', 'auditor@gsms.local',   '内审专员', '$2a$10$ZbQkxTN4l1l3bYpZlfpAeeKri41wYNIYsJD1U4qC/p9QMhz1AQrhi', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES
 ('admin', 1),
 ('caseadmin', 2),
 ('depthead', 3),
 ('handler', 4),
 ('cohandler', 5),
 ('signer', 6),
 ('reader', 7),
 ('auditor', 8);
-- v1.16：角色权限细分到「模块 × 操作」
-- 原有 sys_menu.perm_code 仅控制菜单可见，无法约束接口与按钮。
-- 新增 sys_permission（权限项）与 sys_role_permission（角色授权），
-- 权限码形如 case:view / case:edit，登录时随 JWT 下发，后端 @PreAuthorize 校验，前端按码隐藏按钮。
-- 数据范围（ALL/DEPT/SELF）仍由 sys_role.data_scope 控制，两套机制并行不冲突。

CREATE TABLE IF NOT EXISTS sys_permission (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  module       VARCHAR(40)  NOT NULL COMMENT '模块编码',
  module_name  VARCHAR(50)  NOT NULL COMMENT '模块名称',
  action       VARCHAR(20)  NOT NULL COMMENT 'view/create/edit/delete/export',
  action_name  VARCHAR(50)  NOT NULL COMMENT '操作名称',
  perm_code    VARCHAR(100) NOT NULL COMMENT '权限码',
  sort         INT          NOT NULL DEFAULT 0,
  status       TINYINT      NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_perm_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块操作权限项';

CREATE TABLE IF NOT EXISTS sys_role_permission (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_perm (role_id, permission_id),
  KEY idx_rp_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限项授权';

-- 权限项（固定 ID，便于迁移脚本幂等授权与回滚）
INSERT INTO sys_permission (id, module, module_name, action, action_name, perm_code, sort) VALUES
 (1,  'dashboard', '工作台',   'view',   '查看',   'dashboard:view',   10),
 (2,  'case',      '案件管理', 'view',   '查看',   'case:view',        20),
 (3,  'case',      '案件管理', 'create', '新增',   'case:create',      21),
 (4,  'case',      '案件管理', 'edit',   '编辑',   'case:edit',        22),
 (5,  'case',      '案件管理', 'delete', '删除',   'case:delete',      23),
 (6,  'case',      '案件管理', 'import', '批量导入','case:import',     24),
 (7,  'ledger',    '台账',     'view',   '查看',   'ledger:view',      30),
 (8,  'ledger',    '台账',     'edit',   '编辑',   'ledger:edit',      31),
 (9,  'ledger',    '台账',     'export', '导出',   'ledger:export',    32),
 (10, 'relation',  '争议关联', 'view',   '查看',   'relation:view',    40),
 (11, 'relation',  '争议关联', 'create', '新增',   'relation:create',  41),
 (12, 'relation',  '争议关联', 'delete', '解除',   'relation:delete',  42),
 (13, 'relation',  '争议关联', 'export', '导出',   'relation:export',  43),
 (14, 'deadline',  '期限中心', 'view',   '查看',   'deadline:view',    50),
 (15, 'deadline',  '期限中心', 'edit',   '调整',   'deadline:edit',    51),
 (16, 'todo',      '我的待办', 'view',   '查看',   'todo:view',        60),
 (17, 'todo',      '我的待办', 'create', '分派',   'todo:create',      61),
 (18, 'todo',      '我的待办', 'edit',   '办理',   'todo:edit',        62),
 (19, 'report',    '报表中心', 'view',   '查看',   'report:view',      70),
 (20, 'report',    '报表中心', 'export', '导出',   'report:export',    71),
 (21, 'archive',   '归档库',   'view',   '查看',   'archive:view',     80),
 (22, 'archive',   '归档库',   'edit',   '归档',   'archive:edit',     81),
 (23, 'doc',       '文档',     'view',   '查看下载','doc:view',        90),
 (24, 'doc',       '文档',     'create', '上传',   'doc:create',       91),
 (25, 'doc',       '文档',     'delete', '删除',   'doc:delete',       92),
 (26, 'sys',       '系统管理', 'view',   '查看',   'sys:view',         100),
 (27, 'sys',       '系统管理', 'create', '新增',   'sys:create',       101),
 (28, 'sys',       '系统管理', 'edit',   '编辑',   'sys:edit',         102),
 (29, 'sys',       '系统管理', 'delete', '删除',   'sys:delete',       103),
 (30, 'workflow',  '流程配置', 'view',   '查看',   'workflow:view',    110),
 (31, 'workflow',  '流程配置', 'edit',   '编辑',   'workflow:edit',    111),
 (32, 'workflow',  '流程配置', 'delete', '删除',   'workflow:delete',  112),
 (33, 'audit',     '审计日志', 'view',   '查看',   'audit:view',       120),
 (34, 'audit',     '审计日志', 'export', '导出',   'audit:export',     121),
 (35, 'recycle',   '回收站',   'view',   '查看',   'recycle:view',     130),
 (36, 'recycle',   '回收站',   'edit',   '恢复',   'recycle:edit',     131),
 (37, 'recycle',   '回收站',   'delete', '永久删除','recycle:delete',  132)
ON DUPLICATE KEY UPDATE module_name=VALUES(module_name), action_name=VALUES(action_name), sort=VALUES(sort), status=1;

-- 角色默认授权：管理员全量；案件管理员除系统管理外的全部；其余按职责最小化。
-- 用 perm_code 白名单授权，重复执行不会插入重复行。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'ROLE_SYS_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'ROLE_CASE_ADMIN'
  AND p.module NOT IN ('sys','workflow','audit')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'ROLE_DEPT_HEAD'
  AND p.perm_code IN (
    'dashboard:view','case:view','case:create','case:edit','case:import',
    'ledger:view','ledger:edit','ledger:export',
    'relation:view','relation:create','relation:delete','relation:export',
    'deadline:view','deadline:edit','todo:view','todo:create','todo:edit',
    'report:view','report:export','archive:view','doc:view','doc:create','doc:delete',
    'workflow:view')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'ROLE_HANDLER'
  AND p.perm_code IN (
    'dashboard:view','case:view','case:create','case:edit','case:import',
    'ledger:view','ledger:edit','ledger:export',
    'relation:view','relation:create','relation:delete',
    'deadline:view','deadline:edit','todo:view','todo:create','todo:edit',
    'report:view','archive:view','doc:view','doc:create','doc:delete')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'ROLE_CO_HANDLER'
  AND p.perm_code IN (
    'dashboard:view','case:view','case:edit','ledger:view',
    'relation:view','deadline:view','todo:view','todo:edit',
    'doc:view','doc:create')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'ROLE_COUNTERSIGN'
  AND p.perm_code IN (
    'dashboard:view','case:view','ledger:view','relation:view',
    'todo:view','todo:edit','doc:view')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'ROLE_READER'
  AND p.perm_code IN (
    'dashboard:view','case:view','ledger:view','relation:view',
    'deadline:view','report:view','archive:view','doc:view')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'ROLE_AUDITOR'
  AND p.perm_code IN (
    'dashboard:view','case:view','ledger:view','relation:view',
    'report:view','archive:view','doc:view','audit:view','audit:export')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

-- 系统管理员的角色权限配置页入口
INSERT INTO sys_menu (id, menu_code, menu_name, menu_type, parent_id, path, perm_code, icon, sort, status)
SELECT 22, 'P-SYS-04', '角色权限', 'M', 0, '/system/permissions', 'sys:perm:config', 'Lock', 62, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'P-SYS-04');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.menu_code = 'P-SYS-04'
WHERE r.role_code = 'ROLE_SYS_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
