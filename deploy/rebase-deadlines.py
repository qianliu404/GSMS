"""按新口径重算存量期限：起算日 = 案件发生日期 + 节点时限（工作日）。

仅处理未被人工调整（manual_date 为空）且未完成的记录；
与 WorkDayUtils.plusWorkingDays 保持一致：从起算日起顺延 days 个周一至周五。
"""
import os
import re
import subprocess
from datetime import date, timedelta

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
SQL = """
SELECT d.id, m.occur_date, n.time_limit, d.deadline_date
FROM deadline_reminder d
JOIN case_main m ON m.id = d.case_id
JOIN wf_node_def n ON n.status_code = d.node_code AND n.flow_key = m.case_type
WHERE d.manual_date IS NULL AND d.status <> 'DONE';
"""


def plus_working_days(start: date, days: int) -> date:
    cur, left = start, days
    while left > 0:
        cur += timedelta(days=1)
        if cur.weekday() < 5:
            left -= 1
    return cur


def main():
    out = subprocess.run(
        [MYSQL, "-uroot", f"-p{os.environ.get('MYSQL_ROOT_PASSWORD', '')}", "case_system", "-N", "--default-character-set=utf8mb4", "-e", SQL],
        capture_output=True, check=True,
    ).stdout.decode("utf-8", "ignore")

    updates, skipped = [], 0
    for line in out.splitlines():
        parts = line.strip().split("\t")
        if len(parts) != 4:
            continue
        rid, occur, limit, old = parts
        digits = re.search(r"(\d+)", limit or "")
        if not occur or not digits:
            skipped += 1
            continue
        days = int(digits.group(1))
        if days <= 0:
            skipped += 1
            continue
        new = plus_working_days(date.fromisoformat(occur), days)
        if str(new) != old:
            updates.append(f"UPDATE deadline_reminder SET deadline_date='{new}' WHERE id={rid};")
            print(f"id={rid}  {occur} + {days}工作日 -> {new}（原 {old}）")

    print(f"\n待更新 {len(updates)} 条，跳过 {skipped} 条")
    if updates:
        sql = "\n".join(updates)
        subprocess.run(
            [MYSQL, "-uroot", f"-p{os.environ.get('MYSQL_ROOT_PASSWORD', '')}", "case_system", "-e", sql],
            check=True,
        )
        print("已写入")


if __name__ == "__main__":
    main()
