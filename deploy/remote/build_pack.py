"""为 MySQL 5.7 生成兼容的部署 SQL 与服务器版 nginx 配置。

- init.sql 将 utf8mb4_0900_ai_ci 替换为 utf8mb4_unicode_ci（5.7 不支持 0900 系列）
- nginx.conf 改为监听 8081、root 指向 C:/GSMS/web/dist、日志落 C:/GSMS/data/logs
输出目录：deploy/remote/out
"""
import io
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(parents=True, exist_ok=True)

# ---------- 1. SQL 5.7 适配 ----------
src = (ROOT / "sql" / "init.sql").read_text(encoding="utf-8")
adapted = src.replace("utf8mb4_0900_ai_ci", "utf8mb4_unicode_ci")
adapted = adapted.replace(
    "-- 目标实例：MySQL 8.0.45 / utf8mb4 / utf8mb4_0900_ai_ci",
    "-- 目标实例：MySQL 5.7（192.168.11.179）/ utf8mb4 / utf8mb4_unicode_ci",
)
(OUT / "init-mysql57.sql").write_text(adapted, encoding="utf-8", newline="\n")

# 迁移脚本同样替换（按文件名排序，v1.10 之后为增量）
mig = []
for p in sorted((ROOT / "sql").glob("migrate-*.sql")):
    s = p.read_text(encoding="utf-8").replace("utf8mb4_0900_ai_ci", "utf8mb4_unicode_ci")
    (OUT / p.name).write_text(s, encoding="utf-8", newline="\n")
    mig.append(p.name)

# ---------- 2. nginx 服务器版 ----------
conf = (ROOT / "deploy" / "nginx.conf").read_text(encoding="utf-8")
conf = conf.replace("listen       8080;", "listen       8081;")
conf = conf.replace("root   E:/GSMS/web/dist;", "root   C:/GSMS/web/dist;")
conf = conf.replace("E:/GSMS/data/logs/", "C:/GSMS/data/logs/")
conf = conf.replace(
    "#  说明：本机 80/443 已被系统进程占用，故使用 8080",
    "#  说明：目标服务器 80(IIS)/8080(Tomcat9) 已被占用，故使用 8081",
)
conf = conf.replace("#  安装路径：D:\\nginx（可通过环境变量 NGINX_HOME 覆盖）",
                    "#  安装路径：C:\\GSMS\\nginx")
conf = conf.replace("worker_processes  2;", "worker_processes  4;")
(OUT / "nginx.conf").write_text(conf, encoding="utf-8", newline="\n")

print("生成完成：", OUT)
for f in sorted(OUT.iterdir()):
    print(f"  {f.name}  {f.stat().st_size} bytes")
print("迁移脚本：", ", ".join(mig))
