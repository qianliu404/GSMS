"""
节点自选流转校验：推进可跳过中间节点、退回可回到更早节点。

用法：python deploy/verify-jump-node.py [base_url]
脚本自建临时案件并在结束时删除（走回收站永久删除），不影响既有数据。
"""
import os
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

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


def codes(items):
    return [i.get("code") for i in (items or [])]


token = login()

# 1. 建案：草稿态只有「提交立案」一个出口
_, created = request(token, "POST", "/cases", data={
    "caseType": "INJURY", "caseName": "节点自选流转校验", "employeeName": "自选流转",
    "occurDate": "2026-11-01", "handlerId": "admin", "plantArea": "华东二厂",
    "workshopTeam": "冲压一班", "submit": False})
case_id = created.get("data")
if isinstance(case_id, dict):
    case_id = case_id.get("id")
if not case_id:
    print("建案失败：", created)
    sys.exit(1)

try:
    d = detail(token, case_id)
    check(codes(d.get("forwardTargets")) == ["FILED"], "草稿态可推进至立案", codes(d.get("forwardTargets")))
    check(not codes(d.get("rollbackTargets")), "草稿态无可退回节点", codes(d.get("rollbackTargets")))

    # 2. 提交立案后：可推进目标覆盖全部下游（可跳级），可退回为空
    request(token, "POST", "/cases/%s/submit" % case_id, data={"opinion": "提交立案"})
    d = detail(token, case_id)
    fwd = codes(d.get("forwardTargets"))
    check(d.get("status") == "FILED", "已进入立案节点", d.get("status"))
    check("RECOGNITION" in fwd, "立案可直接推进至认定（跳过中间节点）", fwd)
    check("DISPUTE" in fwd, "立案可推进至争议处理", fwd)
    check("CLOSED" not in fwd and "ARCHIVED" not in fwd, "结案/归档不作为流转目标", fwd)
    check(not codes(d.get("rollbackTargets")), "立案节点无可退回节点（草稿不可退）")

    # 3. 跳级推进：立案 → 认定
    st, body = request(token, "POST", "/cases/%s/transition" % case_id,
                       data={"toStatus": "RECOGNITION", "opinion": "材料齐全，直接进入认定"})
    check(body.get("code") == 0, "跳级推进接口成功", body.get("message"))
    d = detail(token, case_id)
    check(d.get("status") == "RECOGNITION", "案件状态已跳到认定", d.get("status"))
    logs = d.get("statusLogs") or []
    top = logs[0] if logs else {}
    check(top.get("fromStatus") == "FILED" and top.get("toStatus") == "RECOGNITION",
          "流转日志记录立案→认定", "%s→%s" % (top.get("fromStatus"), top.get("toStatus")))

    # 4. 被跳过的节点不应挂着待办期限（节点期限按「进入节点」生成，跳过即不产生）
    _, dl = request(token, "GET", "/deadlines", params={"caseId": case_id, "pageNum": 1, "pageSize": 50})
    rows = (dl.get("data") or {}).get("list", [])
    declare_rows = [x for x in rows if x.get("nodeCode") == "DECLARE"]
    check(all(x.get("status") == "DONE" for x in declare_rows),
          "被跳过的申报节点无待办期限", [x.get("status") for x in declare_rows] or "无记录")

    # 5. 退回目标：认定可退回申报/调查取证/立案
    rbk = codes(d.get("rollbackTargets"))
    check(rbk and rbk[0] == "DECLARE", "退回目标默认最近前驱", rbk)
    check("FILED" in rbk, "认定可退回立案（跨节点）", rbk)

    # 6. 非法退回目标应被拒（认定不能退到下游节点）
    st, body = request(token, "POST", "/cases/%s/rollback" % case_id,
                       data={"opinion": "非法目标校验", "toStatus": "TREATMENT"})
    check(body.get("code") != 0, "退回下游节点被拒绝", body.get("message"))

    # 7. 跨节点退回：认定 → 立案
    st, body = request(token, "POST", "/cases/%s/rollback" % case_id,
                       data={"opinion": "认定材料不足，退回立案重办", "toStatus": "FILED"})
    check(body.get("code") == 0, "退回立案接口成功", body.get("message"))
    d = detail(token, case_id)
    check(d.get("status") == "FILED", "案件已退回立案", d.get("status"))
    logs = d.get("statusLogs") or []
    top = logs[0] if logs else {}
    check(top.get("action") == "ROLLBACK" and top.get("toStatus") == "FILED",
          "流转日志记录退回落痕", "%s %s→%s" % (top.get("action"), top.get("fromStatus"), top.get("toStatus")))

    # 8. 退回后认定期限重新打开，避免节点一直显示已完成
    _, dl2 = request(token, "GET", "/deadlines", params={"caseId": case_id, "pageNum": 1, "pageSize": 50})
    rec_row = next((x for x in (dl2.get("data") or {}).get("list", []) if x.get("nodeCode") == "RECOGNITION"), None)
    check(rec_row is not None and rec_row.get("status") != "DONE",
          "退回后认定期限重新打开", rec_row.get("status") if rec_row else "无记录")

    # 9. 非法推进目标：不能直接流转到归档
    st, body = request(token, "POST", "/cases/%s/transition" % case_id,
                       data={"toStatus": "ARCHIVED", "opinion": "非法目标校验"})
    check(body.get("code") != 0, "直接流转到归档被拒绝", body.get("message"))
finally:
    # 清理：软删除后从回收站永久删除
    request(token, "DELETE", "/cases/%s" % case_id)
    request(token, "DELETE", "/recycle-bin", data={"target": "CASE", "ids": [int(case_id)]})
    print("已清理临时案件 id=%s" % case_id)

print("=" * 46)
print("  通过: %d   失败: %d" % (sum(1 for r in results if r), sum(1 for r in results if not r)))
sys.exit(0 if all(results) else 1)
