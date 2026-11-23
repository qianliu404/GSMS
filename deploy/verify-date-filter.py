"""日期筛选参数绑定回归：GET 查询里的 LocalDate 必须支持 yyyy-MM-dd。

用法：python deploy/verify-date-filter.py [base_url]
覆盖：案件列表日期区间、单端、空串、归档年份、报表导出、期限、审计日志、非法日期提示。
"""
import os
import json
import sys
import urllib.parse
import urllib.request
import urllib.error

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8089"
PREFIX = "/api/v1"
results = []


def check(ok, name, detail=""):
    results.append(ok)
    print(("【通过】" if ok else "【失败】") + name + ((" -- " + str(detail)) if detail else ""))


def login():
    body = json.dumps({"userId": "admin", "password": os.environ.get("GSMS_PASSWORD", "")}).encode()
    req = urllib.request.Request(BASE + PREFIX + "/auth/login", data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())["data"]["token"]


def get(token, path, params=None, binary=False):
    url = BASE + PREFIX + path
    if params is not None:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"Authorization": "Bearer " + token})
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
            if binary:
                return resp.status, raw
            return resp.status, json.loads(raw)
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def ok_json(resp):
    status, body = resp
    return status == 200 and isinstance(body, dict) and body.get("code") == 0


def main():
    token = login()

    # 1. 用户报的原始场景
    params = {"pageNum": 1, "pageSize": 50,
              "occurDateFrom": "2026-11-03", "occurDateTo": "2026-11-21"}
    status, body = get(token, "/cases", params)
    check(ok_json((status, body)), "案件列表日期区间 200",
          f"http={status} code={body.get('code') if isinstance(body, dict) else ''} msg={str(body.get('message'))[:120] if isinstance(body, dict) else ''}")
    if ok_json((status, body)):
        rows = body["data"]["list"]
        dates = [r.get("occurDate") for r in rows if r.get("occurDate")]
        check(all("2026-11-03" <= d <= "2026-11-21" for d in dates),
              "区间筛选结果均在范围内", f"rows={len(rows)}, dates={dates[:5]}")

    # 2. 仅起始 / 仅结束
    for label, one in [("仅起始", {"occurDateFrom": "2026-11-01"}),
                       ("仅结束", {"occurDateTo": "2026-11-30"})]:
        status, body = get(token, "/cases", {"pageNum": 1, "pageSize": 10, **one})
        check(ok_json((status, body)), f"案件列表{label}日期 200")

    # 3. 空串（前端清空筛选时 axios 会带上空串）
    status, body = get(token, "/cases",
                       {"pageNum": 1, "pageSize": 10, "occurDateFrom": "", "occurDateTo": ""})
    check(ok_json((status, body)), "清空日期后空串参数 200")

    # 4. 类型 + 日期组合
    status, body = get(token, "/cases",
                       {"pageNum": 1, "pageSize": 10, "caseType": "INJURY",
                        "occurDateFrom": "2026-01-01", "occurDateTo": "2026-12-31"})
    check(ok_json((status, body)), "案件类型 + 日期区间 200")

    # 5. 归档库按年份筛选
    status, body = get(token, "/cases",
                       {"pageNum": 1, "pageSize": 10, "archiveStatus": 1,
                        "occurDateFrom": "2026-01-01", "occurDateTo": "2026-12-31"})
    check(ok_json((status, body)), "归档库年度筛选 200")

    # 6. 期限列表日期区间（DeadlineQuery）
    status, body = get(token, "/deadlines",
                       {"pageNum": 1, "pageSize": 10,
                        "dateFrom": "2026-01-01", "dateTo": "2026-12-31"})
    check(ok_json((status, body)), "期限列表日期区间 200")

    # 7. 审计日志日期区间（@RequestParam 注解路径，确认全局配置未破坏）
    status, body = get(token, "/audit-logs",
                       {"pageNum": 1, "pageSize": 10,
                        "dateFrom": "2026-01-01", "dateTo": "2026-12-31"})
    check(ok_json((status, body)), "审计日志日期区间 200")

    # 8. 两个导出接口带日期
    for path, label in [("/reports/cases/export", "工伤统计表导出"),
                        ("/reports/archive/export", "归档卷宗目录导出")]:
        status, raw = get(token, path, {"pageNum": 1, "pageSize": 100,
                                        "occurDateFrom": "2026-01-01",
                                        "occurDateTo": "2026-12-31"}, binary=True)
        check(status == 200 and raw[:2] == b"PK", f"{label}带日期筛选返回 xlsx",
              f"http={status} size={len(raw)}")

    # 9. 非法日期：应给出可读提示，不再抛整段转换异常
    status, body = get(token, "/cases",
                       {"pageNum": 1, "pageSize": 5, "occurDateFrom": "2026/11/03"})
    msg = body.get("message", "") if isinstance(body, dict) else ""
    check(isinstance(body, dict) and body.get("code") == 40001,
          "非法日期返回参数校验失败", f"code={body.get('code') if isinstance(body, dict) else ''}")
    check("yyyy-MM-dd" in msg and "Failed to convert property value" not in msg,
          "非法日期提示可读且不含异常栈", msg[:120])

    failed = results.count(False)
    print(f"\n== {len(results) - failed}/{len(results)} passed ==")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
