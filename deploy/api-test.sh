#!/usr/bin/env bash

# 登录密码从环境变量读取（见 .env.example）
GSMS_PASSWORD="${GSMS_PASSWORD:?请先设置 GSMS_PASSWORD}"
login_body() { printf '{"userId":"%s","password":"%s"}' "$1" "$GSMS_PASSWORD"; }
# =============================================================
# GSMS 扩展接口测试：文档中心 / 待办 / 期限 / 报表 / 审计 / 系统管理
# 前置：服务已启动，且已执行过 smoke-test.sh（存在至少一个案件）
# 用法：bash deploy/api-test.sh [baseUrl]
# =============================================================
set -u
BASE="${1:-http://127.0.0.1:8089}"
SAMPLE="E:/GSMS/data/tmp/sample.pdf"
TMP="E:/GSMS/data/tmp"
PASS=0
FAIL=0

ok()  { PASS=$((PASS+1)); echo "  [PASS] $1"; }
bad() { FAIL=$((FAIL+1)); echo "  [FAIL] $1 -- ${2:-}"; }

jget() { python -c "import sys,json;d=json.load(sys.stdin)
p='$1'.split('.')
v=d
try:
    for k in p:
        v = v[int(k)] if isinstance(v,list) else v.get(k)
except Exception:
    v=None
print('' if v is None else v)" 2>/dev/null; }

echo "=== 登录（admin）==="
TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" \
  -d "$(login_body admin)" | jget data.token)
[ -n "$TOKEN" ] && ok "登录成功" || { bad "登录" "无 token"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"

echo "=== 取一个案件 ID ==="
CASE_ID=$(curl -s -H "$AUTH" "$BASE/api/v1/cases?pageNum=1&pageSize=1" | jget data.list.0.id)
if [ -z "$CASE_ID" ]; then bad "无案件可用" "请先运行 smoke-test.sh"; exit 1; fi
ok "caseId=$CASE_ID"

echo "=== 1. 文档上传 ==="
UP=$(curl -s -X POST "$BASE/api/v1/cases/$CASE_ID/documents" -H "$AUTH" \
  -F "file=@$SAMPLE" -F "docType=EVIDENCE" -F "sensitiveFlag=0" -F "watermark=1")
DOC_ID=$(echo "$UP" | jget data)
[ -n "$DOC_ID" ] && ok "上传成功 docId=$DOC_ID" || bad "上传" "$UP"

echo "=== 2. 文档列表 ==="
DOC_COUNT=$(curl -s -H "$AUTH" "$BASE/api/v1/cases/$CASE_ID/documents" | python -c "import sys,json;print(len(json.load(sys.stdin)['data']))" 2>/dev/null)
[ "$DOC_COUNT" -ge 1 ] && ok "文档数=$DOC_COUNT" || bad "文档列表" "$DOC_COUNT"

echo "=== 3. 下载并校验水印 ==="
curl -s -H "$AUTH" "$BASE/api/v1/documents/$DOC_ID/download" -o "$TMP/dl.pdf"
ORIG_SIZE=$(stat -c%s "$SAMPLE" 2>/dev/null || echo 0)
DL_SIZE=$(stat -c%s "$TMP/dl.pdf" 2>/dev/null || echo 0)
[ "$DL_SIZE" -gt "$ORIG_SIZE" ] && ok "水印后体积 $ORIG_SIZE -> $DL_SIZE（已叠加水印层）" || bad "水印体积" "$ORIG_SIZE vs $DL_SIZE"
head -c 5 "$TMP/dl.pdf" | grep -q "%PDF" && ok "下载文件为合法 PDF" || bad "PDF 头" "非 PDF"

echo "=== 4. 上传新版本 ==="
VER=$(curl -s -X POST "$BASE/api/v1/documents/$DOC_ID/versions" -H "$AUTH" \
  -F "file=@$SAMPLE" -F "note=补充材料")
NEW_DOC=$(echo "$VER" | jget data)
VER_NO=$(curl -s -H "$AUTH" "$BASE/api/v1/documents/$NEW_DOC/versions" | python -c "
import sys,json
d=json.load(sys.stdin)['data']
print(max(x['version'] for x in d) if d else 0)" 2>/dev/null)
[ "$VER_NO" = "2" ] && ok "版本号递增到 2" || bad "版本" "$VER_NO"

echo "=== 5. 我的待办 ==="
TODO=$(curl -s -H "$AUTH" "$BASE/api/v1/todos")
TODO_TOTAL=$(echo "$TODO" | python -c "import sys,json;d=json.load(sys.stdin)['data'];print(len(d['list']))" 2>/dev/null)
ok "待办聚合返回 $TODO_TOTAL 条（任务/会签/期限）"

echo "=== 6. 期限中心 ==="
DL_TOTAL=$(curl -s -H "$AUTH" "$BASE/api/v1/deadlines?pageNum=1&pageSize=10" | jget data.total)
[ "${DL_TOTAL:-0}" -ge 1 ] && ok "期限节点数=$DL_TOTAL" || bad "期限" "${DL_TOTAL:-0}"
SCAN=$(curl -s -X POST -H "$AUTH" "$BASE/api/v1/deadlines/scan")
ok "手动扫描: $(echo "$SCAN" | jget data)"

echo "=== 7. 报表中心 ==="
SUM_TOTAL=$(curl -s -H "$AUTH" "$BASE/api/v1/reports/case-summary" | jget data.total)
[ -n "$SUM_TOTAL" ] && ok "案件总览 total=$SUM_TOTAL" || bad "case-summary" ""
AMOUNT=$(curl -s -H "$AUTH" "$BASE/api/v1/reports/amount" | jget data.total)
ok "金额汇总 total=$AMOUNT"
EFF=$(curl -s -H "$AUTH" "$BASE/api/v1/reports/efficiency" | jget data.taskTotal)
ok "协同效率 任务数=$EFF"
OVD=$(curl -s -H "$AUTH" "$BASE/api/v1/reports/overdue" | jget data.overdueRate)
ok "超期率=$OVD%"

echo "=== 8. Excel 导出 ==="
curl -s -H "$AUTH" "$BASE/api/v1/reports/cases/export" -o "$TMP/export.xlsx"
EXP_SIZE=$(stat -c%s "$TMP/export.xlsx" 2>/dev/null || echo 0)
if [ "$EXP_SIZE" -gt 1000 ]; then
  head -c 2 "$TMP/export.xlsx" | grep -q "PK" && ok "导出 xlsx 合法（$EXP_SIZE 字节）" || bad "xlsx 魔数" ""
else
  bad "导出" "size=$EXP_SIZE"
fi

echo "=== 9. 审计日志 ==="
AUDIT_TOTAL=$(curl -s -H "$AUTH" "$BASE/api/v1/audit-logs?pageNum=1&pageSize=5" | jget data.total)
[ "${AUDIT_TOTAL:-0}" -ge 1 ] && ok "审计日志 $AUDIT_TOTAL 条" || bad "审计日志" "${AUDIT_TOTAL:-0}"
# 校验审计参数已脱敏
LEAK=$(curl -s -H "$AUTH" "$BASE/api/v1/audit-logs?pageNum=1&pageSize=50" | python -c "
import sys,json,re
d=json.load(sys.stdin)['data']['list']
bad=[x for x in d if x.get('requestParams') and re.search(r'\d{6}\d{8}\d{3}[\dXx]', x['requestParams'])]
print(len(bad))" 2>/dev/null)
[ "$LEAK" = "0" ] && ok "审计参数无完整身份证号泄露" || bad "脱敏" "命中 $LEAK 条"

echo "=== 10. 系统管理 ==="
USERS=$(curl -s -H "$AUTH" "$BASE/api/v1/users?pageNum=1&pageSize=5" | jget data.total)
[ "${USERS:-0}" -ge 8 ] && ok "用户数=$USERS" || bad "用户" "${USERS:-0}"
ROLES=$(curl -s -H "$AUTH" "$BASE/api/v1/roles" | python -c "import sys,json;print(len(json.load(sys.stdin)['data']))" 2>/dev/null)
[ "$ROLES" = "8" ] && ok "角色数=8" || bad "角色" "$ROLES"
DICT=$(curl -s -H "$AUTH" "$BASE/api/v1/dicts/case_status" | python -c "import sys,json;print(len(json.load(sys.stdin)['data']))" 2>/dev/null)
[ "${DICT:-0}" -ge 15 ] && ok "案件状态字典=$DICT 项" || bad "字典" "$DICT"

echo "=== 11. 权限隔离：普通用户访问用户管理应 40301 ==="
HTOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" \
  -d "$(login_body handler)" | jget data.token)
DENY=$(curl -s -H "Authorization: Bearer $HTOKEN" "$BASE/api/v1/users" | jget code)
[ "$DENY" = "40301" ] && ok "越权访问被拒绝(40301)" || bad "越权" "$DENY"

echo "=== 12. 表单精简：不填工号 / 部门 / 描述仍可建档 ==="
CREATE=$(curl -s -X POST "$BASE/api/v1/cases" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"caseType":"INJURY","caseName":"接口测试-精简建档","employeeName":"测试员工","occurDate":"2026-11-10","handlerId":"handler","plantArea":"华东二厂","submit":false}')
NID=$(echo "$CREATE" | jget data)
if [ -n "$NID" ] && [ "$NID" != "" ]; then
  DET=$(curl -s -H "$AUTH" "$BASE/api/v1/cases/$NID")
  DEPT=$(echo "$DET" | python -c "import sys,json;print(json.load(sys.stdin)['data'].get('departmentName') or '')" 2>/dev/null)
  EMP=$(echo "$DET" | python -c "import sys,json;print(json.load(sys.stdin)['data'].get('employeeId') or 'NULL')" 2>/dev/null)
  DESC=$(echo "$DET" | python -c "import sys,json;print(json.load(sys.stdin)['data'].get('description') or 'NULL')" 2>/dev/null)
  [ "$DEPT" = "人力资源部" ] && ok "部门由承办人自动带出($DEPT)" || bad "部门带出" "$DEPT"
  [ "$EMP" = "NULL" ] && ok "未填工号保持为空" || bad "工号" "$EMP"
  [ "$DESC" = "NULL" ] && ok "未填描述保持为空" || bad "描述" "$DESC"
else
  bad "精简建档" "$CREATE"
fi

echo "=== 13. 厂区字典自定义 ==="
AREAS=$(curl -s -H "$AUTH" "$BASE/api/v1/dicts/plant_area" | python -c "import sys,json;print(len(json.load(sys.stdin)['data']))" 2>/dev/null)
[ "${AREAS:-0}" -ge 1 ] && ok "厂区字典可读($AREAS 项)" || bad "厂区字典" "$AREAS"
ADDED=$(curl -s -X POST "$BASE/api/v1/dicts/plant_area/items" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"接口测试厂区"}')
AID=$(echo "$ADDED" | jget data.id)
[ -n "$AID" ] && ok "新增自定义厂区(id=$AID)" || bad "新增厂区" "$ADDED"
DENY_DEL=$(curl -s -X DELETE "$BASE/api/v1/dicts/plant_area/items/$AID" -H "Authorization: Bearer $HTOKEN")
[ "$(echo "$DENY_DEL" | jget code)" = "40301" ] && ok "非管理员不可删除厂区" || bad "厂区删除权限" "$DENY_DEL"
DEL=$(curl -s -X DELETE "$BASE/api/v1/dicts/plant_area/items/$AID" -H "$AUTH")
[ "$(echo "$DEL" | jget code)" = "0" ] && ok "管理员可删除厂区" || bad "删除厂区" "$DEL"

echo "=== 14. 用户软删除 ==="
TS=$(date +%s | tail -c 6)
NEWUID="deltest$TS"
NEWUSER=$(curl -s -X POST "$BASE/api/v1/users" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"userId\":\"$NEWUID\",\"userName\":\"删除测试$TS\",\"deptId\":2,\"password\":\"Test@123\",\"status\":1,\"roleIds\":[7]}")
NID=$(echo "$NEWUSER" | jget data)
[ -n "$NID" ] && ok "创建待删除用户(id=$NID)" || bad "创建用户" "$NEWUSER"
DELCODE=$(curl -s -X DELETE "$BASE/api/v1/users/$NID" -H "$AUTH" | jget code)
[ "$DELCODE" = "0" ] && ok "软删除成功" || bad "软删除" "$DELCODE"
LEFT=$(curl -s -H "$AUTH" "$BASE/api/v1/users?keyword=$NEWUID&pageNum=1&pageSize=5" | jget data.total)
[ "$LEFT" = "0" ] && ok "删除后列表不可见" || bad "删除后可见性" "$LEFT"
SELF=$(curl -s -X DELETE "$BASE/api/v1/users/1" -H "$AUTH" | jget code)
[ "$SELF" = "40301" ] && ok "禁止删除当前登录账号" || bad "自删保护" "$SELF"
HID=$(curl -s -H "$AUTH" "$BASE/api/v1/users?keyword=handler&pageNum=1&pageSize=20" | python -c "
import sys,json
print(next((u['id'] for u in json.load(sys.stdin)['data']['list'] if u['userId']=='handler'), ''))" 2>/dev/null)
BUSY=$(curl -s -X DELETE "$BASE/api/v1/users/$HID" -H "$AUTH" | jget code)
[ "$BUSY" = "40901" ] && ok "在办案件承办人禁止删除" || bad "在办案件保护" "$BUSY"

echo "=== 15. 回收站：删除 → 恢复 → 永久删除 ==="
RCASE=$(curl -s -X POST "$BASE/api/v1/cases" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"caseType":"INJURY","caseName":"接口测试-回收站","employeeName":"回收","occurDate":"2026-11-12","handlerId":"handler","submit":false}')
RCID=$(echo "$RCASE" | jget data)
[ -n "$RCID" ] && ok "建案件(id=$RCID)" || bad "建案件" "$RCASE"
curl -s -X DELETE "$BASE/api/v1/cases/$RCID" -H "$AUTH" > /dev/null
RCKW=$(python -c "import urllib.parse;print(urllib.parse.quote('接口测试-回收站'))" 2>/dev/null)
INBIN=$(curl -s -H "$AUTH" "$BASE/api/v1/recycle-bin?target=CASE&keyword=$RCKW&pageNum=1&pageSize=5" | jget data.total)
[ "$INBIN" = "1" ] && ok "删除后进入案件回收站" || bad "案件回收站" "$INBIN"
HIDDEN=$(curl -s -H "$AUTH" "$BASE/api/v1/cases/$RCID" | jget code)
[ "$HIDDEN" != "0" ] && ok "已删除案件不在业务列表可见" || bad "删除后仍可见" "$HIDDEN"
REST=$(curl -s -X POST "$BASE/api/v1/recycle-bin/restore" -H "$AUTH" -H "Content-Type: application/json" -d "{\"target\":\"CASE\",\"ids\":[$RCID]}" | jget data)
[ "$REST" = "1" ] && ok "恢复案件" || bad "恢复案件" "$REST"
BACK=$(curl -s -H "$AUTH" "$BASE/api/v1/cases/$RCID" | jget code)
[ "$BACK" = "0" ] && ok "恢复后可正常访问" || bad "恢复后访问" "$BACK"
curl -s -X DELETE "$BASE/api/v1/cases/$RCID" -H "$AUTH" > /dev/null
PURGE=$(curl -s -X DELETE "$BASE/api/v1/recycle-bin" -H "$AUTH" -H "Content-Type: application/json" -d "{\"target\":\"CASE\",\"ids\":[$RCID]}" | jget data)
[ "$PURGE" = "1" ] && ok "永久删除案件" || bad "永久删除" "$PURGE"
GONE=$(curl -s -H "$AUTH" "$BASE/api/v1/cases/$RCID" | jget code)
[ "$GONE" != "0" ] && ok "永久删除后不可再访问" || bad "永久删除残留" "$GONE"

RUSER=$(curl -s -X POST "$BASE/api/v1/users" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"userId\":\"rcuser$TS\",\"userName\":\"回收用户$TS\",\"deptId\":2,\"password\":\"Test@123\",\"status\":1,\"roleIds\":[7]}")
RUID=$(echo "$RUSER" | jget data)
curl -s -X DELETE "$BASE/api/v1/users/$RUID" -H "$AUTH" > /dev/null
UBIN=$(curl -s -H "$AUTH" "$BASE/api/v1/recycle-bin?target=USER&keyword=rcuser$TS&pageNum=1&pageSize=5" | jget data.total)
[ "$UBIN" = "1" ] && ok "删除用户进入回收站" || bad "用户回收站" "$UBIN"
UREST=$(curl -s -X POST "$BASE/api/v1/recycle-bin/restore" -H "$AUTH" -H "Content-Type: application/json" -d "{\"target\":\"USER\",\"ids\":[$RUID]}" | jget data)
[ "$UREST" = "1" ] && ok "恢复用户" || bad "恢复用户" "$UREST"
curl -s -X DELETE "$BASE/api/v1/users/$RUID" -H "$AUTH" > /dev/null
curl -s -X DELETE "$BASE/api/v1/recycle-bin" -H "$AUTH" -H "Content-Type: application/json" -d "{\"target\":\"USER\",\"ids\":[$RUID]}" > /dev/null
ULEFT=$(curl -s -H "$AUTH" "$BASE/api/v1/users/$RUID" | jget code)
[ "$ULEFT" != "0" ] && ok "用户永久删除后不可再访问" || bad "用户永久删除残留" "$ULEFT"
CADENY=$(curl -s -H "Authorization: Bearer $(curl -s -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" -d "$(login_body caseadmin)" | jget data.token)" "$BASE/api/v1/recycle-bin?target=USER" | jget code)
[ "$CADENY" = "40301" ] && ok "案件管理员不可查看用户回收站" || bad "回收站权限" "$CADENY"

echo
echo "==============================================="
echo "  通过: $PASS   失败: $FAIL"
echo "==============================================="
[ "$FAIL" -eq 0 ] || exit 1
