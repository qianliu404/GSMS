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
