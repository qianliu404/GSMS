#!/usr/bin/env bash

# 登录密码从环境变量读取（见 .env.example）
GSMS_PASSWORD="${GSMS_PASSWORD:?请先设置 GSMS_PASSWORD}"
login_body() { printf '{"userId":"%s","password":"%s"}' "$1" "$GSMS_PASSWORD"; }
set -u
BASE="http://127.0.0.1:8089"
J='Content-Type: application/json'

token() {
  curl -s -X POST "$BASE/api/v1/auth/login" -H "$J" -d "$(login_body admin)" \
    | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])"
}
TOKEN=$(token)
AUTH="Authorization: Bearer $TOKEN"

echo "=== 1) 建一个仲裁测试案件 ==="
CREATE=$(curl -s -X POST "$BASE/api/v1/cases" -H "$AUTH" -H "$J" -d '{
  "caseType":"ARBITRATION",
  "caseName":"回填验证-加班费争议",
  "employeeId":"EMPTEST",
  "employeeName":"回填验证",
  "idCardNo":"110101199001019999",
  "departmentId":"2",
  "departmentName":"人力资源部",
  "occurDate":"2026-11-01",
  "description":"回填验证用，可删除",
  "handlerId":"admin",
  "submit":false,
  "arbitration":{"arbitrationNo":"验证字〔2026〕第 001 号","applyDate":"2026-11-01"}
}')
CID=$(echo "$CREATE" | python -c "import sys,json;d=json.load(sys.stdin);print(d.get('data') or '')")
echo "caseId=$CID"
if [ -z "$CID" ]; then echo "$CREATE"; exit 1; fi

curl -s -X POST "$BASE/api/v1/cases/$CID/submit" -H "$AUTH" -H "$J" -d '{"opinion":"提交立案"}' > /dev/null
step() {
  curl -s -X POST "$BASE/api/v1/cases/$CID/transition" -H "$AUTH" -H "$J" \
    -d "{\"toStatus\":\"$1\",\"opinion\":\"$2\"}" | python -c "import sys,json;d=json.load(sys.stdin);print(' ->',d.get('code'),d.get('message'))"
}

echo "=== 2) FILED → EVIDENCE（不传 acceptDate，应按节点自动补）==="
step EVIDENCE "材料齐全"
python - <<EOF
import subprocess, json
r = subprocess.run(['curl','-s','-H','$AUTH','$BASE/api/v1/cases/$CID'], capture_output=True, text=True)
d = json.loads(r.stdout)['data']['arbitration']
print('   acceptDate =', d.get('acceptDate'), ' resultType =', d.get('resultType'))
EOF

echo "=== 3) 推进到 AWARD ==="
step CONCIL "转调解"
step HEARING "转开庭"
step AWARD "开庭完毕"

echo "=== 4) AWARD → PERFORM（显式传 resultType=MEDIATION）==="
curl -s -X POST "$BASE/api/v1/cases/$CID/transition" -H "$AUTH" -H "$J" \
  -d '{"toStatus":"PERFORM","opinion":"调解达成","resultType":"MEDIATION"}' \
  | python -c "import sys,json;d=json.load(sys.stdin);print(' ->',d.get('code'),d.get('message'))"

python - <<EOF
import subprocess, json
r = subprocess.run(['curl','-s','-H','$AUTH','$BASE/api/v1/cases/$CID'], capture_output=True, text=True)
d = json.loads(r.stdout)['data']['arbitration']
print('   acceptDate =', d.get('acceptDate'), ' resultType =', d.get('resultType'))
EOF

echo "=== 5) 清理：删除测试案件 ==="
curl -s -X DELETE "$BASE/api/v1/cases/$CID" -H "$AUTH" | python -c "import sys,json;d=json.load(sys.stdin);print(' delete ->',d.get('code'),d.get('message'))"
echo "TEST_CASE_ID=$CID"
