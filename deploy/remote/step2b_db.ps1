$ErrorActionPreference = 'Stop'
$mysql = 'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe'
$rootPwd = $env:MYSQL_ROOT_PASSWORD

Invoke-WebRequest 'http://192.168.2.209:8010/init-mysql57.sql' -OutFile 'C:\GSMS\sql\init-mysql57.sql' -UseBasicParsing
"downloaded_bytes=$((Get-Item 'C:\GSMS\sql\init-mysql57.sql').Length)"

cmd /c "`"$mysql`" -uroot -p$rootPwd --default-character-set=utf8mb4 < `"C:\GSMS\sql\init-mysql57.sql`""
"import_exit=$LASTEXITCODE"

& $mysql -uroot "-p$rootPwd" --default-character-set=utf8mb4 -N -e @"
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='case_system';
SELECT COUNT(*) FROM case_system.sys_user;
SELECT COUNT(*) FROM case_system.sys_role;
SELECT COUNT(*) FROM case_system.sys_permission;
SELECT COUNT(*) FROM case_system.sys_role_permission;
SELECT COUNT(*) FROM case_system.wf_node_def;
SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='case_system' AND table_name='wf_node_def' AND column_name='next_codes';
"@ 2>&1 | Where-Object { $_ -notmatch 'Warning' }
