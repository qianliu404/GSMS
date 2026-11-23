$mysql = 'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe'
$rootPwd = $env:MYSQL_ROOT_PASSWORD
$tables = @('audit_log','case_arbitration_ext','case_document','case_injury_ext','case_main','case_member','case_relation','case_status_log','countersign','deadline_reminder','notification','sys_dept','sys_dict','sys_menu','sys_permission','sys_role','sys_role_menu','sys_role_permission','sys_user','sys_user_role','task','wf_instance','wf_node_def')

$parts = foreach ($t in $tables) { "SELECT '$t' t, COUNT(*) c FROM $t" }
$countSql = "SELECT GROUP_CONCAT(CONCAT(t,':',c) ORDER BY t SEPARATOR ' ') FROM (" + ($parts -join ' UNION ALL ') + ") x;"

$fpSql = "SELECT CONCAT(" +
  "(SELECT MD5(GROUP_CONCAT(case_no,case_name,employee_name ORDER BY id)) FROM case_main),','," +
  "(SELECT MD5(GROUP_CONCAT(user_id,user_name ORDER BY id)) FROM sys_user),','," +
  "(SELECT MD5(GROUP_CONCAT(role_code,role_name ORDER BY id)) FROM sys_role),','," +
  "(SELECT MD5(GROUP_CONCAT(node_name ORDER BY id)) FROM wf_node_def),','," +
  "(SELECT MD5(GROUP_CONCAT(menu_code,menu_name ORDER BY id)) FROM sys_menu))"

$out = & $mysql -uroot "-p$rootPwd" --default-character-set=utf8mb4 -N case_system -e $countSql 2>$null
"REMOTE $out"
$fp = & $mysql -uroot "-p$rootPwd" --default-character-set=utf8mb4 -N case_system -e $fpSql 2>$null
"REMOTE_FP $fp"
