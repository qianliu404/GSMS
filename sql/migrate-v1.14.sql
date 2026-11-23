-- v1.14：新增「争议关联」模块菜单与角色授权
-- 关联关系本身复用 case_relation，无需改表

INSERT INTO sys_menu (id, menu_code, menu_name, menu_type, parent_id, path, perm_code, icon, sort, status)
SELECT 21, 'CASE_RELATION', '争议关联', 'M', 0, '/relations', 'case:relation:view', 'Link', 24, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'CASE_RELATION');

-- 管理员在初始化时已被全量授权（role_id=1 由 SELECT 全表插入），这里补齐其余业务角色
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code = 'CASE_RELATION'
WHERE r.id <> 8
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
