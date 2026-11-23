-- =============================================================
-- V1.7 迁移：工伤台账升级为「台账」
-- 1) 菜单名称由「工伤台账」改为「台账」（路由与权限编码不变）
-- 2) 台账同时覆盖工伤与劳动仲裁案件；全部/工伤/劳动仲裁三套字段配置互相隔离
-- 3) 案件列表 / 工伤案件 / 劳动仲裁下线批量导出，导出统一由台账提供
-- =============================================================

UPDATE sys_menu
   SET menu_name = '台账'
 WHERE menu_code = 'INJURY_LEDGER';

-- 校验
SELECT '台账菜单' AS chk, menu_code, menu_name, path, perm_code
  FROM sys_menu
 WHERE menu_code = 'INJURY_LEDGER';
