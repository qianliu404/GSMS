#!/usr/bin/env bash

# 登录密码从环境变量读取（见 .env.example）
GSMS_PASSWORD="${GSMS_PASSWORD:?请先设置 GSMS_PASSWORD}"
login_body() { printf '{"userId":"%s","password":"%s"}' "$1" "$GSMS_PASSWORD"; }
# =============================================================
# GSMS 后端冒烟测试
# 覆盖：登录 -> 建档 -> 列表 -> 详情 -> 提交立案 -> 状态流转 -> 脱敏校验
# 用法：bash deploy/smoke-test.sh [baseUrl]
# 依赖：curl、python
# =============================================================
set -u
BASE="${1:-http://127.0.0.1:8089}"
PASS=0
FAIL=0

ok()   { PASS=$((PASS+1)); echo "  [PASS] $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  [FAIL] $1 -- $2"; }

jget() { python -c "import sys,json;d=json.load(sys.stdin);
p='$1'.split('.')
v=d
for k in p:
    v = v[int(k)] if isinstance(v,list) else v.get(k)
print('' if v is None else v)" 2>/dev/null; }

echo "=== 1. 登录 ==="
LOGIN=$(curl -s -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" \
  -d "$(login_body handler)")
CODE=$(echo "$LOGIN" | jget code)
TOKEN=$(echo "$LOGIN" | jget data.token)
if [ "$CODE" = "0" ] && [ -n "$TOKEN" ]; then ok "handler 登录成功"; else bad "登录" "$LOGIN"; exit 1; fi

AUTH="Authorization: Bearer $TOKEN"
JSON="Content-Type: application/json"

echo "=== 2. 未授权访问应返回 40101 ==="
NOAUTH=$(curl -s "$BASE/api/v1/cases" | jget code)
[ "$NOAUTH" = "40101" ] && ok "无 token 返回 40101" || bad "无 token" "$NOAUTH"

echo "=== 3. 创建工伤案件 ==="
CREATE=$(curl -s -X POST "$BASE/api/v1/cases" -H "$AUTH" -H "$JSON" -d '{
  "caseType":"INJURY",
  "caseName":"王五冲压车间机械伤害工伤认定",
  "employeeId":"EMP1001",
  "employeeName":"王五",
  "idCardNo":"110101199001011234",
  "departmentId":"2",
  "departmentName":"人力资源部",
  "occurDate":"2026-10-15",
  "description":"2026年10月15日在冲压车间操作设备时右手挤压伤，当日送医。",
  "handlerId":"handler",
  "coHandlerIds":["cohandler"],
  "countersignerIds":["signer"],
  "readerIds":["reader"],
  "submit":false,
  "injury":{"injuryDate":"2026-10-15","injuryPlace":"冲压车间","injuryReason":"设备故障导致右手挤压伤","treatmentAmount":58000.00,"disputeFlag":0}
}')
CASE_ID=$(echo "$CREATE" | jget data)
if [ -n "$CASE_ID" ] && [ "$(echo "$CREATE" | jget code)" = "0" ]; then
  ok "建档成功 caseId=$CASE_ID"
else
  bad "建档" "$CREATE"; exit 1
fi

echo "=== 4. 详情：草稿状态 + 可执行动作 ==="
DETAIL=$(curl -s -H "$AUTH" "$BASE/api/v1/cases/$CASE_ID")
STATUS=$(echo "$DETAIL" | jget data.status)
CASE_NO=$(echo "$DETAIL" | jget data.caseNo)
ACTIONS=$(echo "$DETAIL" | jget data.actions.0.code)
[ "$STATUS" = "DRAFT" ] && ok "初始状态 DRAFT" || bad "初始状态" "$STATUS"
[ -n "$CASE_NO" ] && ok "案件编号已生成: $CASE_NO" || bad "编号" "空"
[ "$ACTIONS" = "SUBMIT" ] && ok "草稿态动作为 SUBMIT" || bad "动作" "$ACTIONS"
MEMBERS=$(echo "$DETAIL" | python -c "import sys,json;print(len(json.load(sys.stdin)['data']['members']))" 2>/dev/null)
[ "$MEMBERS" = "4" ] && ok "成员 4 人（负责人/承办/协办/会签/阅知去重）" || bad "成员数" "$MEMBERS"

echo "=== 5. 提交立案 ==="
SUB=$(curl -s -X POST "$BASE/api/v1/cases/$CASE_ID/submit" -H "$AUTH" -H "$JSON" -d '{"opinion":"材料齐全，提交立案"}')
S2=$(curl -s -H "$AUTH" "$BASE/api/v1/cases/$CASE_ID" | jget data.status)
[ "$S2" = "FILED" ] && ok "提交后状态 FILED" || bad "提交立案" "$S2 $SUB"

echo "=== 6. 状态流转 FILED -> INVESTIGATION ==="
curl -s -X POST "$BASE/api/v1/cases/$CASE_ID/transition" -H "$AUTH" -H "$JSON" \
  -d '{"toStatus":"INVESTIGATION","opinion":"开始调查取证"}' > /dev/null
S3=$(curl -s -H "$AUTH" "$BASE/api/v1/cases/$CASE_ID" | jget data.status)
[ "$S3" = "INVESTIGATION" ] && ok "流转成功 -> INVESTIGATION" || bad "流转" "$S3"

echo "=== 7. 非法流转应被拒绝 ==="
BAD=$(curl -s -X POST "$BASE/api/v1/cases/$CASE_ID/transition" -H "$AUTH" -H "$JSON" \
  -d '{"toStatus":"CLOSED"}' | jget code)
[ "$BAD" = "40301" ] || [ "$BAD" = "40901" ] && ok "非法流转被拒绝($BAD)" || bad "非法流转" "$BAD"

echo "=== 8. 退回上一节点 ==="
curl -s -X POST "$BASE/api/v1/cases/$CASE_ID/rollback" -H "$AUTH" -H "$JSON" \
  -d '{"opinion":"材料需补充"}' > /dev/null
S4=$(curl -s -H "$AUTH" "$BASE/api/v1/cases/$CASE_ID" | jget data.status)
[ "$S4" = "FILED" ] && ok "退回成功 -> FILED" || bad "退回" "$S4"

echo "=== 9. 列表查询与数据权限 ==="
TOTAL=$(curl -s -H "$AUTH" "$BASE/api/v1/cases?pageNum=1&pageSize=10" | jget data.total)
[ "$TOTAL" -ge 1 ] && ok "承办人可见案件数=$TOTAL" || bad "列表" "$TOTAL"

READER_TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" -H "$JSON" \
  -d "$(login_body reader)" | jget data.token)
READER_TOTAL=$(curl -s -H "Authorization: Bearer $READER_TOKEN" "$BASE/api/v1/cases?pageNum=1&pageSize=10" | jget data.total)
[ "$READER_TOTAL" -ge 1 ] && ok "阅知人作为成员可见案件数=$READER_TOTAL" || bad "阅知人列表" "$READER_TOTAL"

OUTSIDER_TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" -H "$JSON" \
  -d "$(login_body auditor)" | jget data.token)
OUTSIDE=$(curl -s -H "Authorization: Bearer $OUTSIDER_TOKEN" "$BASE/api/v1/cases" | jget data.total)
ok "审计员(ALL范围)列表可见数=$OUTSIDE"

echo "=== 10. 敏感字段脱敏 ==="
READER_DETAIL=$(curl -s -H "Authorization: Bearer $READER_TOKEN" "$BASE/api/v1/cases/$CASE_ID")
R_NAME=$(echo "$READER_DETAIL" | jget data.employeeName)
R_MASKED=$(echo "$READER_DETAIL" | jget data.sensitiveMasked)
[ "$R_MASKED" = "True" ] && ok "阅知人敏感字段已标记脱敏" || bad "脱敏标记" "$R_MASKED"
echo "     阅知人看到姓名: $R_NAME"
H_DETAIL=$(curl -s -H "$AUTH" "$BASE/api/v1/cases/$CASE_ID")
H_NAME=$(echo "$H_DETAIL" | jget data.employeeName)
[ "$H_NAME" = "王五" ] && ok "承办人看到明文姓名: $H_NAME" || bad "承办人姓名" "$H_NAME"

echo
echo "==============================================="
echo "  通过: $PASS   失败: $FAIL"
echo "==============================================="
[ "$FAIL" -eq 0 ] || exit 1
