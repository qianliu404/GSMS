"""台账 / 统计表 / 归档 三类导出校验（后端接口层）。

用法：python deploy/verify-ledger-export.py [base_url]
依赖：openpyxl
"""
import os
import io
import json
import sys
import urllib.parse
import urllib.request

import openpyxl

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8089"
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


def fetch(token, path, params=None):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(req) as resp:
        return resp.read()


def sheet(raw):
    return openpyxl.load_workbook(io.BytesIO(raw)).active


def header_of(ws):
    return [ws.cell(row=3, column=c).value for c in range(1, ws.max_column + 1)]


def main():
    token = login()

    # 1) 台账默认列（不传 columns）
    ws = sheet(fetch(token, "/api/v1/ledger/export", {"pageNum": 1, "pageSize": 100}))
    head = header_of(ws)
    check(head[:6] == ["序号", "类型", "案号", "厂区", "车间班组 / 部门", "姓名"],
          "不传 columns 时与前端「全部」视图默认列一致", head)
    check(len(head) == 13, "默认 13 列", len(head))
    check("工伤及劳动仲裁台账" in (ws["A1"].value or ""), "台账标题行", ws["A1"].value)
    check(ws.max_row > 3, "台账有数据", f"rows={ws.max_row - 3}")

    # 2) 台账自定义列：仲裁字段 + 只筛仲裁
    cols = "seq,type,arbitrationNo,employeeName,applyDate,acceptDate,hearingDate,resultType,awardAmount,performStatus"
    ws2 = sheet(fetch(token, "/api/v1/ledger/export",
                      {"pageNum": 1, "pageSize": 100, "caseType": "ARBITRATION", "columns": cols}))
    head2 = header_of(ws2)
    expect2 = ["序号", "类型", "仲裁案号", "姓名", "申请日期", "受理日期", "开庭时间", "结案方式", "裁决/调解金额", "履行状态"]
    check(head2 == expect2, "自定义列导出表头与所选字段一致", head2)
    types = {ws2.cell(row=r, column=2).value for r in range(4, ws2.max_row + 1)}
    check(types == {"仲裁"}, "仲裁筛选下只有仲裁行", types)
    check(ws2.max_row > 3, "仲裁台账有数据", f"rows={ws2.max_row - 3}")

    # 3) 案件统计表导出不再附带案件清单
    wb = openpyxl.load_workbook(io.BytesIO(fetch(token, "/api/v1/reports/cases/export",
                                                 {"pageNum": 1, "pageSize": 100})))
    check(wb.sheetnames == ["工伤事故人员信息统计表"], "统计表导出只含一个工作表", wb.sheetnames)
    ws3 = wb.active
    check([ws3.cell(row=3, column=c).value for c in range(1, 13)][0] == "序号", "统计表表头从序号开始")
    check(ws3.max_column == 12, "统计表 12 列", ws3.max_column)

    # 4) 归档卷宗目录
    wb4 = openpyxl.load_workbook(io.BytesIO(fetch(token, "/api/v1/reports/archive/export",
                                                  {"pageNum": 1, "pageSize": 100, "archiveStatus": 1})))
    check(wb4.sheetnames == ["归档卷宗目录"], "归档导出工作表名", wb4.sheetnames)
    ws4 = wb4.active
    check(ws4.cell(row=1, column=1).value == "案件编号", "归档导出表头以案件编号开始")
    check(ws4.max_column == 11, "归档导出 11 列（含导出人/时间）", ws4.max_column)

    ok = all(results)
    print("\n== %d/%d passed ==" % (sum(1 for r in results if r), len(results)))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
