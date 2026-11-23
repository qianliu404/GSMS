"""用本机最新库结构 + init.sql 种子数据，生成目标机可用的初始化脚本。

背景：init.sql 的建表段落后于实际迁移（缺 next_codes 等字段），直接用会启动失败。
做法：mysqldump --no-data 导结构与代码完全匹配的 DDL，替换 collation 为 5.7 支持值；
      从 init.sql 抽取所有 INSERT 语句作为种子数据。
"""
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(parents=True, exist_ok=True)

MYSQLDUMP = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
DB = "case_system"
USER = "root"
PWD = os.environ.get("MYSQL_ROOT_PASSWORD", "")

# 1. 结构
proc = subprocess.run(
    [MYSQLDUMP, "--no-data", "--skip-comments", "--skip-add-locks", "--set-gtid-purged=OFF",
     "--default-character-set=utf8mb4", f"-u{USER}", f"-p{PWD}", "-h127.0.0.1", DB],
    capture_output=True, text=True, encoding="utf-8", errors="replace",
)
if proc.returncode != 0:
    raise SystemExit("mysqldump 失败: " + proc.stderr[:500])
schema = proc.stdout
schema = schema.replace("utf8mb4_0900_ai_ci", "utf8mb4_unicode_ci")
# 去掉 dump 里的库级语句（由我们统一创建）
schema = re.sub(r"^USE `?case_system`?;\s*$", "", schema, flags=re.M)

# 2. 种子 INSERT
init_sql = (ROOT / "sql" / "init.sql").read_text(encoding="utf-8")
blocks = []
for m in re.finditer(r"^INSERT INTO .*?;\s*$", init_sql, flags=re.M | re.S):
    text = m.group(0).strip()
    # 只保留纯 VALUES 插入；INSERT ... SELECT（角色/菜单授权）由下方按实际数据单独生成
    if re.search(r"\bSELECT\b", text, flags=re.I):
        continue
    blocks.append(text)
inserts = "\n\n".join(blocks)

# 3. 角色权限授权：来自 sys_role_permission 的实际数据（保证 8 角色权限与本地一致）
rp = subprocess.run(
    [r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe", "--default-character-set=utf8mb4",
     f"-u{USER}", f"-p{PWD}", "-h127.0.0.1", "-N", "-e",
     "select role_id, permission_id from case_system.sys_role_permission order by role_id, permission_id"],
    capture_output=True, text=True, encoding="utf-8", errors="replace",
)
if rp.returncode != 0:
    raise SystemExit("读取角色权限失败: " + rp.stderr[:300])
rp_values = ",\n".join(
    f"({line.split()[0]}, {line.split()[1]})" for line in rp.stdout.strip().splitlines() if line.strip()
)
role_perm_insert = f"INSERT INTO sys_role_permission (role_id, permission_id) VALUES\n{rp_values};"

# 4. 组装
header = f"""-- =============================================================
-- 工伤与劳动仲裁案件协同管理系统  初始化脚本（MySQL 5.7 适配版）
-- 结构来源：本机 MySQL 8 生产库 mysqldump（与代码完全一致）
-- 字符集：utf8mb4 / utf8mb4_unicode_ci（5.7 不支持 0900 系列）
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 < init-mysql57.sql
-- =============================================================

DROP DATABASE IF EXISTS case_system;
CREATE DATABASE case_system DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
USE case_system;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

"""
footer = """

SET FOREIGN_KEY_CHECKS = 1;
"""
extra = """
INSERT INTO sys_menu (id, menu_code, menu_name, menu_type, parent_id, path, perm_code, icon, sort, status)
VALUES (22, 'P-SYS-04', '角色权限', 'M', 0, '/system/permissions', 'sys:perm:config', 'Lock', 62, 1);

INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 22);

-- 系统管理员拥有全部菜单（init.sql 用 INSERT...SELECT 写入，需单独补）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
"""

final = header + schema.strip() + "\n\n-- ---------------- 种子数据 ----------------\n\n" + inserts + "\n\n" + extra + "\n" + role_perm_insert + footer

target = OUT / "init-mysql57.sql"
target.write_text(final, encoding="utf-8", newline="\n")
print("生成:", target, len(final), "字节")
print("INSERT 块:", len(blocks), "个；role_permission 行:", len(rp_values.splitlines()))
print("含 next_codes:", "next_codes" in final)
