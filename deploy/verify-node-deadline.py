"""
节点截止日期 / 临近超期 / 鉴定休假天数 / 履行款 校验。

用法：python deploy/verify-node-deadline.py [base_url]
脚本自建临时案件并结束时删除（走回收站永久删除），不影响既有数据。
"""
import os
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8089"
PREFIX = "/api/v1"
results = []

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def check(ok, name, detail=""):
    results.append(ok)
    print(("【通过】" if ok else "【失败】") + name + ((" -- " + str(detail)) if detail else ""))


def request(token, method, path, params=None, data=None):
    url = BASE + PREFIX + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method, headers={
        "Authorization": "Bearer " + token, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, json.loads(raw) if raw else {}


def login():
    _, body = request("", "POST", "/auth/login", data={"userId": "admin", "password": os.environ.get("GSMS_PASSWORD", "")})
    return body["data"]["token"]


def detail(token, case_id):
    _, body = request(token, "GET", "/cases/%s" % case_id)
    return body.get("data") or {}


def working_days(start, days):
    """与后端 WorkDayUtils.plusWorkingDays 一致：跳过周六周日，不含起始日"""
    d = start
    left = days
    while left > 0:
        d += timedelta(days=1)
        if d.weekday() < 5:
            left -= 1
    return d


def create(token, case_type, name, extra):
    payload = {"caseType": case_type, "caseName": name, "employeeName": name[:6],
               "occurDate": "2026-11-01", "handlerId": "admin", "plantArea": "华东二厂",
               "submit": False}
    payload.update(extra)
    _, body = request(token, "POST", "/cases", data=payload)
    cid = body.get("data")
    return cid.get("id") if isinstance(cid, dict) else cid


def deadlines(token, case_id):
    _, body = request(token, "GET", "/deadlines", params={"caseId": case_id, "pageNum": 1, "pageSize": 50})
    return (body.get("data") or {}).get("list", [])


def node_dl(d, code):
    return next((x for x in (d.get("nodeDeadlines") or []) if x.get("statusCode") == code), None)


token = login()
today = date.today()
created = []

try:
    # ---------- 1. 工伤：进入立案节点自动建期限（按工作日） ----------
    injury_id = create(token, "INJURY", "节点期限校验工伤",
                       {"injury": {"injuryPlace": "车间", "injuryReason": "操作设备受伤"}})
    created.append(injury_id)
    check(bool(injury_id), "工伤案件创建成功", injury_id)

    request(token, "POST", "/cases/%s/submit" % injury_id, data={"opinion": "提交立案"})
    d = detail(token, injury_id)
    check(d.get("status") == "FILED", "已进入立案节点", d.get("status"))

    rows = deadlines(token, injury_id)
    filed = next((r for r in rows if r.get("nodeCode") == "FILED"), None)
    expect = working_days(today, 3)
    check(filed is not None, "立案节点已生成期限记录", len(rows))
    check(filed and filed.get("deadlineDate") == expect.isoformat(),
          "立案截止日期 = 进入日 + 3 个工作日", "%s / 期望 %s" % (filed and filed.get("deadlineDate"), expect))
    check(filed and filed.get("remindDays") == 7, "提醒窗口为 7 个自然日", filed and filed.get("remindDays"))

    # 草稿没有数值时限，不应生成记录
    check(not any(r.get("nodeCode") == "DRAFT" for r in rows), "草稿节点（时限为—）不生成期限")

    # ---------- 2. 详情返回节点截止日期 ----------
    dl_filed = node_dl(d, "FILED")
    check(dl_filed is not None, "详情返回立案节点截止日期", dl_filed)
    check(dl_filed and dl_filed.get("deadlineDate") == expect.isoformat(),
          "详情截止日期与期限记录一致", dl_filed and dl_filed.get("deadlineDate"))
    check(dl_filed and dl_filed.get("state") in ("NORMAL", "DUE_SOON"),
          "当前节点状态为正常/临近超期", dl_filed and dl_filed.get("state"))
    check(dl_filed and dl_filed.get("remainDays") is not None, "返回剩余天数", dl_filed and dl_filed.get("remainDays"))

    # ---------- 3. 鉴定节点：进入时算截止日期，办结时回填休假天数 ----------
    _, body = request(token, "POST", "/cases/%s/transition" % injury_id,
                      data={"toStatus": "APPRAISAL", "opinion": "直接进入鉴定"})
    check(body.get("code") == 0, "推进至鉴定节点成功", body.get("message"))
    d = detail(token, injury_id)
    check(d.get("status") == "APPRAISAL", "案件处于鉴定节点", d.get("status"))

    dl_app = node_dl(d, "APPRAISAL")
    check(dl_app and dl_app.get("deadlineDate") == working_days(today, 30).isoformat(),
          "鉴定截止日期 = 进入日 + 30 个工作日", dl_app and dl_app.get("deadlineDate"))
    check(dl_app and dl_app.get("state") == "NORMAL", "鉴定节点状态正常", dl_app and dl_app.get("state"))
    check((node_dl(d, "FILED") or {}).get("state") == "DONE", "已离开的节点标记 DONE",
          (node_dl(d, "FILED") or {}).get("state"))

    # 鉴定办结：结论 + 休假天数随提交回填
    _, body = request(token, "POST", "/cases/%s/transition" % injury_id,
                      data={"toStatus": "TREATMENT", "opinion": "鉴定完成", "appraisalLevel": "九级",
                            "restDays": 90})
    check(body.get("code") == 0, "鉴定办结成功", body.get("message"))
    d = detail(token, injury_id)
    check(d.get("status") == "TREATMENT", "案件处于待遇处理", d.get("status"))
    check((d.get("injury") or {}).get("restDays") == 90, "休假天数已写入档案",
          (d.get("injury") or {}).get("restDays"))
    check((d.get("injury") or {}).get("appraisalLevel") == "九级", "鉴定结论仍正常回填",
          (d.get("injury") or {}).get("appraisalLevel"))
    check((node_dl(d, "APPRAISAL") or {}).get("state") == "DONE", "鉴定节点办结后标记 DONE",
          (node_dl(d, "APPRAISAL") or {}).get("state"))

    # ---------- 4. 仲裁：履行款回填 ----------
    arb_id = create(token, "ARBITRATION", "节点期限校验仲裁",
                    {"arbitration": {"arbitrationNo": "ZS-TEST-0001", "applyDate": "2026-11-01"}})
    created.append(arb_id)
    request(token, "POST", "/cases/%s/submit" % arb_id, data={"opinion": "提交立案"})
    rows = deadlines(token, arb_id)
    afiled = next((r for r in rows if r.get("nodeCode") == "FILED"), None)
    check(afiled and afiled.get("deadlineDate") == working_days(today, 5).isoformat(),
          "仲裁立案截止日期 = 进入日 + 5 个工作日", afiled and afiled.get("deadlineDate"))

    _, body = request(token, "POST", "/cases/%s/transition" % arb_id,
                      data={"toStatus": "PERFORM", "opinion": "进入履行"})
    check(body.get("code") == 0, "推进至履行节点成功", body.get("message"))
    d = detail(token, arb_id)
    check(d.get("status") == "PERFORM", "案件处于履行节点", d.get("status"))

    _, body = request(token, "POST", "/cases/%s/close" % arb_id,
                      data={"opinion": "履行完毕结案", "closeType": "AWARD", "performAmount": 128000.50})
    check(body.get("code") == 0, "履行节点结案成功", body.get("message"))
    d = detail(token, arb_id)
    check((d.get("arbitration") or {}).get("performAmount") == 128000.50, "履行款已写入档案",
          (d.get("arbitration") or {}).get("performAmount"))
    check((d.get("arbitration") or {}).get("performDate") is not None, "履行日期已登记",
          (d.get("arbitration") or {}).get("performDate"))

    # ---------- 5. 临近超期口径 ----------
    _, scan = request(token, "POST", "/deadlines/scan")
    check(scan.get("code") == 0, "期限扫描接口可用", scan.get("message"))
    _, body = request(token, "GET", "/deadlines", params={"pageNum": 1, "pageSize": 100})
    rows = (body.get("data") or {}).get("list", [])
    soon = [r for r in rows if r.get("dueSoon")]
    check(all(r.get("remainDays") is not None and 0 <= r.get("remainDays") < 7 for r in soon),
          "临近超期仅覆盖剩余 0~6 天", len(soon))
    check(all(r.get("statusName") == "临近超期" for r in soon), "临近超期状态名正确")

finally:
    for cid in created:
        if cid:
            request(token, "DELETE", "/cases/%s" % cid)
            request(token, "DELETE", "/recycle-bin", data={"target": "CASE", "ids": [cid]})

print("=" * 46)
passed = sum(1 for r in results if r)
print("  通过: %d   失败: %d" % (passed, len(results) - passed))
sys.exit(0 if passed == len(results) else 1)
