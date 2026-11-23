-- V1.18 推送设置模块
-- 1) 推送配置表：自定义推送日期/周期/字段/webhook，支持预览与定时推送
CREATE TABLE IF NOT EXISTS notify_config (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  config_name   VARCHAR(100) NOT NULL COMMENT '配置名称',
  enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  cycle_type    VARCHAR(20)  NOT NULL DEFAULT 'DAILY' COMMENT '周期：DAILY/WEEKLY/MONTHLY',
  week_days     VARCHAR(30)  DEFAULT NULL COMMENT '周推送星期，逗号分隔 1-7（周一=1）',
  month_days    VARCHAR(60)  DEFAULT NULL COMMENT '月推送日期，逗号分隔 1-31',
  push_time     VARCHAR(5)   NOT NULL DEFAULT '09:00' COMMENT '每日推送时间 HH:mm',
  ahead_days    INT          NOT NULL DEFAULT 7 COMMENT '提前天数：距截止不足 N 天纳入推送',
  include_overdue TINYINT    NOT NULL DEFAULT 1 COMMENT '是否包含已超期 1是 0否',
  fields        VARCHAR(500) NOT NULL DEFAULT 'caseNo,caseName,nodeName,deadlineDate,remainDays,handlerName'
                COMMENT '推送字段，逗号分隔',
  webhook       VARCHAR(500) DEFAULT NULL COMMENT '企业微信群机器人 webhook，为空则使用全局默认',
  remark        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  last_push_time DATETIME    DEFAULT NULL COMMENT '最近一次推送时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送设置表';

-- 2) 推送历史表：记录每次推送的结果，便于追溯
CREATE TABLE IF NOT EXISTS notify_history (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  config_id   BIGINT       DEFAULT NULL COMMENT '配置ID，手动测试推送为 NULL',
  config_name VARCHAR(100) DEFAULT NULL COMMENT '配置名称',
  push_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推送时间',
  matched     INT          NOT NULL DEFAULT 0 COMMENT '命中案件数',
  result      VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAIL',
  message     TEXT         DEFAULT NULL COMMENT '推送内容摘要',
  error       VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  PRIMARY KEY (id),
  KEY idx_config (config_id),
  KEY idx_time (push_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送历史表';

-- 3) 权限点：notify:view / notify:edit
INSERT INTO sys_permission (module, module_name, action, action_name, perm_code, sort)
SELECT 'notify', CONVERT(0xe68ea8e98081e8aebee7bdae USING utf8), 'view', CONVERT(0xe69fa5e79c8b USING utf8), 'notify:view', 140
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code='notify:view');
INSERT INTO sys_permission (module, module_name, action, action_name, perm_code, sort)
SELECT 'notify', CONVERT(0xe68ea8e98081e8aebee7bdae USING utf8), 'edit', CONVERT(0xe7bc96e8be91 USING utf8), 'notify:edit', 141
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code='notify:edit');

-- 4) 菜单：推送设置（挂系统管理，排序 66）
INSERT INTO sys_menu (menu_code, menu_name, menu_type, parent_id, path, perm_code, icon, sort, status)
SELECT 'P-SYS-05', CONVERT(0xe68ea8e98081e8aebee7bdae USING utf8), 'M', 0, '/system/notify', 'notify:view', 'Bell', 66, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code='P-SYS-05');

-- 5) 管理员角色授予新权限与菜单（role_id=1 系统管理员）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE module='notify'
AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=1 AND rp.permission_id=sys_permission.id);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE menu_code='P-SYS-05'
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=1 AND rm.menu_id=sys_menu.id);
