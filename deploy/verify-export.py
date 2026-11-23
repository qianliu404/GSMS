"""导出案件 Excel 校验：工伤事故人员信息统计表 + 案件清单双工作表。

用法：python deploy/verify-export.py [base_url]
依赖：openpyxl（已随环境安装）
"""
import os
import io
import json
import sys
import urllib.parse
import urllib.request

import openpyxl

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8089"
STAT_COLS = 12
HEADERS = [
    "序号", "厂区", "车间班组", "姓名", "受伤原因及日期", "小班长", "大班长",
    "工伤认定情况", "劳动能力鉴定结论", "一次性伤残金申领", "一次性医疗金申领", "一次性就业金申领",
]

results = []


def check(ok, name, detail=""):
    results.append(ok)
    print(("【通过】" if ok else "【失败】") + name + ((" -- " + str(detail)) if detail else ""))


def login():
    body = json.dumps({"userId": "admin", "password": os.environ.get("GSMS_PASSWORD", "")}).encode()
    req = urllib.request.Request(BASE + "/api/v1/auth/login", data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())["data"]["token"]


def download(token, query=""):
    url = BASE + "/api/v1/reports/cases/export" + query
    req = urllib.request.Request(url, headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(req) as resp:
        return resp.read()


def main():
    token = login()
    raw = download(token, "?pageNum=1&pageSize=100")
    check(len(raw) > 2000, "导出接口返回文件", f"{len(raw)} bytes")

    wb = openpyxl.load_workbook(io.BytesIO(raw))
    check(wb.sheetnames == ["工伤事故人员信息统计表"], "仅统计表一个工作表（案件清单已下线）", wb.sheetnames)

    ws = wb["工伤事故人员信息统计表"]
    title = ws["A1"].value or ""
    check("工伤事故人员信息统计表" in title, "标题行为统计表名称", title)
    merged = sorted(str(r) for r in ws.merged_cells.ranges)
    check(merged == ["A1:L1", "A2:L2"], "标题与留痕行横向合并 12 列", merged)
    check("导出人" in (ws["A2"].value or ""), "留痕行含导出人", ws["A2"].value)

    head = [ws.cell(row=3, column=c).value for c in range(1, STAT_COLS + 1)]
    check(head == HEADERS, "表头 12 列与模板一致", head)
    check(ws["A1"].font.sz == 16 and ws["A1"].font.b, "标题 16 号加粗")
    check(ws["A3"].font.b and ws["A3"].border.bottom.style == "thin", "表头加粗带边框")
    check(ws["A4"].border.bottom.style == "thin", "数据行带边框")
    check(ws.freeze_panes == "A4", "冻结前 3 行表头", ws.freeze_panes)

    data_rows = list(range(4, ws.max_row + 1))
    check(len(data_rows) > 0, "统计表有数据行", f"rows={len(data_rows)}")
    seq = [ws.cell(row=r, column=1).value for r in data_rows]
    check(seq == list(range(1, len(data_rows) + 1)), "序号连续", seq[:5])
    check(all(ws.cell(row=r, column=4).value for r in data_rows), "姓名列非空")
    check(all(ws.cell(row=r, column=5).value for r in data_rows), "受伤原因及日期列非空")

    # 只看仲裁时统计表应为空表（仍保留标题与表头）
    raw2 = download(token, "?pageNum=1&pageSize=100&caseType=ARBITRATION")
    ws3 = openpyxl.load_workbook(io.BytesIO(raw2))["工伤事故人员信息统计表"]
    check(ws3.max_row == 3, "仅仲裁时统计表只留标题与表头", f"max_row={ws3.max_row}")

    ok = all(results)
    print("\n== %d/%d passed ==" % (sum(1 for r in results if r), len(results)))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
