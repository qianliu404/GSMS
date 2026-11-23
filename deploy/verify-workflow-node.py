"""流程节点配置闭环校验：新增自动接链、拓扑可见、成环拒绝、停用保护、删除自动重连。

用法：python deploy/verify-workflow-node.py [base_url]
脚本自建临时节点并在结束时删除；执行前后对比节点表快照，确保真实配置恢复原状。
"""
import os
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8089"
PREFIX = "/api/v1"
OUT = Path("E:/GSMS/deploy/shots/workflow")
NAME = "流程联动测试"
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
    status, body = request("", "POST", "/auth/login",
                           data={"userId": "admin", "password": os.environ.get("GSMS_PASSWORD", "")})
    return body["data"]["token"]


def node_list(token):
    _, body = request(token, "GET", "/workflow/nodes")
    return body.get("data") or []


def snapshot(nodes):
    return {n["flowKey"] + ":" + n["statusCode"]: n for n in nodes}


def pick(nodes, flow, code=None, name=None):
    for node in nodes:
        if node["flowKey"] != flow:
            continue
        if code is not None and node["statusCode"] != code:
            continue
        if name is not None and node["nodeName"] != name:
            continue
        return node
    return None


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    token = login()
    before = snapshot(node_list(token))
    (OUT / "before.json").write_text(json.dumps(before, ensure_ascii=False, indent=1), encoding="utf-8")

    temp_id = None
    try:
        # 1. 插在草稿之后：应接链并继承原后继
        status, body = request(token, "POST", "/workflow/nodes", data={
            "flowKey": "INJURY", "nodeName": NAME, "insertAfter": 1,
            "timeLimit": "3 天", "mainRole": "测试", "keyMaterial": "测试材料",
            "sysAction": "测试动作", "allowRollback": True})
        check(status == 200 and body.get("code") == 0, "新增节点接口成功",
              f"http={status} code={body.get('code')} msg={body.get('message')}")
        temp_id = body.get("data")
        nodes = node_list(token)
        created = pick(nodes, "INJURY", name=NAME)
        draft = pick(nodes, "INJURY", code="DRAFT")
        check(created is not None, "新节点出现在节点列表")
        check(created and created["sortNo"] == 2, "新节点插在草稿之后", created and created["sortNo"])
        check(created and created["nextCodes"] == "FILED", "新节点继承前驱原后继", created and created["nextCodes"])
        check(draft and created and draft["nextCodes"] == created["statusCode"],
              "前驱改为指向新节点", draft and draft["nextCodes"])
        (OUT / "after-create.json").write_text(json.dumps(snapshot(nodes), ensure_ascii=False, indent=1),
                                             encoding="utf-8")

        # 2. 流程图定义应包含新节点且草稿指向它
        _, defs = request(token, "GET", "/workflow/definitions")
        injury = next((d for d in defs.get("data") or [] if d.get("caseType") == "INJURY"), None)
        codes = [n["code"] for n in (injury or {}).get("nodes", [])]
        draft_def = next((n for n in (injury or {}).get("nodes", []) if n["code"] == "DRAFT"), None)
        check(created is not None and created["statusCode"] in codes, "流程图包含新节点")
        check(draft_def is not None and created is not None and created["statusCode"] in draft_def["next"],
              "流程图草稿的后继包含新节点", draft_def and draft_def["next"])

        # 3. 成环应拒绝：新节点指向草稿，而草稿指向新节点
        status, body = request(token, "PUT", f"/workflow/nodes/{temp_id}", data={
            "flowKey": "INJURY", "nodeName": NAME, "nextCodes": "DRAFT", "allowRollback": True})
        check(body.get("code") == 40001 and "环" in (body.get("message") or ""),
              "成环配置被拒绝", body.get("message"))

        # 4. 停用应拒绝：草稿只剩这一条出路
        status, body = request(token, "PUT", f"/workflow/nodes/{temp_id}/enabled",
                               params={"enabled": "false"})
        check(body.get("code") == 40001 and "没有可流转" in (body.get("message") or ""),
              "会使前驱断链的停用被拒绝", body.get("message"))

        # 5. 删除：前驱应恢复为内置后继
        status, body = request(token, "DELETE", f"/workflow/nodes/{temp_id}")
        check(status == 200 and body.get("code") == 0, "删除节点成功", body.get("message"))
        temp_id = None
        nodes = node_list(token)
        draft = pick(nodes, "INJURY", code="DRAFT")
        check(draft is not None and draft.get("nextCodes") is None, "前驱恢复为内置拓扑", draft and draft.get("nextCodes"))

        # 6. 插到末尾：应落在归档之前
        status, body = request(token, "POST", "/workflow/nodes", data={
            "flowKey": "INJURY", "nodeName": NAME + "二", "insertAfter": 999,
            "timeLimit": "1 天", "mainRole": "测试", "allowRollback": True})
        temp_id = body.get("data")
        nodes = node_list(token)
        tail = pick(nodes, "INJURY", name=NAME + "二")
        archived = pick(nodes, "INJURY", code="ARCHIVED")
        check(tail is not None and tail["nextCodes"] == "ARCHIVED", "插到末尾时落在归档之前",
              tail and tail["nextCodes"])
        check(tail is not None and archived is not None and tail["sortNo"] < archived["sortNo"],
              "新节点排序在归档之前", tail and archived and (tail["sortNo"], archived["sortNo"]))
        request(token, "DELETE", f"/workflow/nodes/{temp_id}")
        temp_id = None
    finally:
        if temp_id:
            request(token, "DELETE", f"/workflow/nodes/{temp_id}")

    # 7. 对比快照：节点数、后继、排序、启停状态恢复原状
    after = snapshot(node_list(token))
    (OUT / "after.json").write_text(json.dumps(after, ensure_ascii=False, indent=1), encoding="utf-8")
    check(set(before.keys()) == set(after.keys()), "节点集合恢复原状",
          f"before={len(before)} after={len(after)}")
    diff = []
    for key, old in before.items():
        new = after.get(key)
        if new is None:
            diff.append(key + ":missing")
            continue
        for field in ("nextCodes", "sortNo", "enabled"):
            if old.get(field) != new.get(field):
                diff.append(f"{key}.{field}: {old.get(field)} -> {new.get(field)}")
    check(not diff, "后继 / 排序 / 启停与执行前一致", "; ".join(diff[:5]))

    failed = results.count(False)
    print(f"\n== {len(results) - failed}/{len(results)} passed ==")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
