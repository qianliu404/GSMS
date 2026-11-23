"""以本机当前库数据为准，生成覆盖远端 case_system 的数据同步脚本。

- 只导数据（不含 DDL）：远端表结构与本机同源，来自同一份 mysqldump 结构
- TRUNCATE 全部表后重灌，保证远端与当前数据完全一致
- 处理 5.7 兼容：无 0900 collation；关闭严格模式与外键校验
"""
import os
from pathlib import Path
import subprocess

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
RAW = OUT / "data-raw.sql"

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

tables = subprocess.run(
    [MYSQL, "-uroot", f"-p{os.environ.get('MYSQL_ROOT_PASSWORD', '')}", "-h127.0.0.1", "-N", "--default-character-set=utf8mb4",
     "-e", "select table_name from information_schema.tables where table_schema='case_system' order by table_name"],
    capture_output=True, text=True, encoding="utf-8",
).stdout.split()

raw = RAW.read_text(encoding="utf-8")

header = """-- ============================================================
-- 工伤与劳动仲裁案件协同管理系统  数据同步脚本（以本机当前数据为准）
-- 生成时间：2026-11-23
-- 用途：清空目标库业务与配置数据后，按当前系统数据重灌
-- 目标：MySQL 5.7 / utf8mb4
-- ============================================================

SET NAMES utf8mb4;
SET SESSION sql_mode = 'NO_ENGINE_SUBSTITUTION';
SET FOREIGN_KEY_CHECKS = 0;
SET UNIQUE_CHECKS = 0;
USE case_system;

-- ---------- 清空 ----------
"""

truncates = "\n".join(f"TRUNCATE TABLE `{t}`;" for t in tables)

footer = """

SET UNIQUE_CHECKS = 1;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------- 校验 ----------
SELECT 'case_main' AS t, COUNT(*) AS c FROM case_main
UNION ALL SELECT 'sys_user', COUNT(*) FROM sys_user
UNION ALL SELECT 'sys_role', COUNT(*) FROM sys_role
UNION ALL SELECT 'sys_user_role', COUNT(*) FROM sys_user_role
UNION ALL SELECT 'sys_role_permission', COUNT(*) FROM sys_role_permission
UNION ALL SELECT 'sys_role_menu', COUNT(*) FROM sys_role_menu
UNION ALL SELECT 'sys_menu', COUNT(*) FROM sys_menu
UNION ALL SELECT 'wf_node_def', COUNT(*) FROM wf_node_def
UNION ALL SELECT 'audit_log', COUNT(*) FROM audit_log
UNION ALL SELECT 'notification', COUNT(*) FROM notification
UNION ALL SELECT 'case_document', COUNT(*) FROM case_document;
"""

final = header + truncates + "\n\n-- ---------- 数据 ----------\n\n" + raw.strip() + footer
target = OUT / "data-sync.sql"
target.write_text(final, encoding="utf-8", newline="\n")
print("生成:", target, target.stat().st_size, "字节")
print("表数:", len(tables))
