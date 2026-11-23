$ErrorActionPreference = 'Stop'
$mysql = 'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe'
$rootPwd = $env:MYSQL_ROOT_PASSWORD

# 1. 导入初始化脚本（含 CREATE DATABASE case_system）
$args = "-uroot -p$rootPwd --default-character-set=utf8mb4"
cmd /c "`"$mysql`" $args < `"C:\GSMS\sql\init-mysql57.sql`""
"import_exit=$LASTEXITCODE"

# 2. 应用账号（localhost 与 127.0.0.1 各建一个，JDBC 走 TCP）
& $mysql -uroot "-p$rootPwd" --default-character-set=utf8mb4 -e @"
CREATE USER IF NOT EXISTS 'case_app'@'localhost' IDENTIFIED BY '$env:DB_PASSWORD';
CREATE USER IF NOT EXISTS 'case_app'@'127.0.0.1' IDENTIFIED BY '$env:DB_PASSWORD';
GRANT ALL PRIVILEGES ON case_system.* TO 'case_app'@'localhost';
GRANT ALL PRIVILEGES ON case_system.* TO 'case_app'@'127.0.0.1';
FLUSH PRIVILEGES;
"@ 2>&1 | Where-Object { $_ -notmatch 'Warning' }

# 3. 校验
& $mysql -uroot "-p$rootPwd" --default-character-set=utf8mb4 -N -e @"
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='case_system';
SELECT COUNT(*) FROM case_system.sys_user;
SELECT COUNT(*) FROM case_system.sys_permission;
SELECT @@version;
"@ 2>&1 | Where-Object { $_ -notmatch 'Warning' }

# 4. 应用账号连通性
& $mysql -ucase_app -p$env:DB_PASSWORD -h127.0.0.1 --default-character-set=utf8mb4 -N -e "select count(*) from case_system.sys_menu;" 2>&1 |
    Where-Object { $_ -notmatch 'Warning' }
