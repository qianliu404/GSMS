-- =============================================================
-- V1.5 迁移：回收站
-- 1) 新增「回收站」菜单（案件 / 用户的已删除记录，可恢复或永久删除）
-- =============================================================

INSERT INTO sys_menu (menu_code, menu_name, menu_type, parent_id, path, perm_code, icon, sort, status) VALUES
 ('RECYCLE_BIN', '回收站', 'M', 0, '/recycle-bin', 'sys:recycle:list', 'Delete', 64, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), path = VALUES(path),
  perm_code = VALUES(perm_code), icon = VALUES(icon), sort = VALUES(sort), status = 1;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM sys_role r
  JOIN sys_menu m ON m.menu_code = 'RECYCLE_BIN'
 WHERE r.role_code IN ('ROLE_SYS_ADMIN', 'ROLE_CASE_ADMIN')
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 校验
SELECT '回收站菜单' AS chk, m.id, m.path, GROUP_CONCAT(r.role_code) AS roles
  FROM sys_menu m
  LEFT JOIN sys_role_menu rm ON rm.menu_id = m.id
  LEFT JOIN sys_role r ON r.id = rm.role_id
 WHERE m.menu_code = 'RECYCLE_BIN'
 GROUP BY m.id, m.path;
