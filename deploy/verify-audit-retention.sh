#!/usr/bin/env bash

# 登录密码从环境变量读取（见 .env.example）
GSMS_PASSWORD="${GSMS_PASSWORD:?请先设置 GSMS_PASSWORD}"
login_body() { printf '{"userId":"%s","password":"%s"}' "$1" "$GSMS_PASSWORD"; }
# 审计日志保留期验证：一年前的记录自动/手动删除，一年内的保留，非管理员不能触发
# 用法：bash deploy/verify-audit-retention.sh [apiBase]
set -u
BASE="${1:-http://127.0.0.1:8089}"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
DB_ARGS=(--default-character-set=utf8mb4 -h127.0.0.1 -ucase_app -p"$DB_PASSWORD" case_system)
PASS=0
FAIL=0
ok()  { PASS=$((PASS+1)); echo "  [PASS] $1"; }
bad() { FAIL=$((FAIL+1)); echo "  [FAIL] $1 -- ${2:-}"; }
sql() { "$MYSQL" "${DB_ARGS[@]}" -N -e "$1" 2>/dev/null; }
login() {
  curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "$(login_body "$1")" |
    python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])"
}
jcode() { python -c "import sys,json;print(json.load(sys.stdin).get('code'))"; }

cleanup_test_rows() {
  sql "DELETE FROM audit_log WHERE user_id='retention-test'"
}

echo "=== 审计日志保留期验证 ==="
cleanup_test_rows

AT=$(login admin)
RT=$(login reader)

# 1. 插入一条一年前与一条近期日志
sql "INSERT INTO audit_log (user_id,user_name,operation,module,business_id,request_ip,request_params,result,trace_id,operate_time)
     VALUES ('retention-test','保留期测试','RETENTION_TEST_EXPIRED','TEST','expired','127.0.0.1','{}','SUCCESS','rt-expired','2025-01-01 00:00:00');
     INSERT INTO audit_log (user_id,user_name,operation,module,business_id,request_ip,request_params,result,trace_id,operate_time)
     VALUES ('retention-test','保留期测试','RETENTION_TEST_KEEP','TEST','keep','127.0.0.1','{}','SUCCESS','rt-keep','2026-11-20 00:00:00')"
cnt=$(sql "SELECT COUNT(*) FROM audit_log WHERE user_id='retention-test'")
[ "$cnt" = "2" ] && ok "插入过期与近期测试日志" || bad "插入测试日志" "$cnt"

# 2. 管理员触发清理：过期删除、近期保留
resp=$(curl -s -X POST -H "Authorization: Bearer $AT" "$BASE/api/v1/audit-logs/cleanup")
code=$(echo "$resp" | jcode)
deleted=$(echo "$resp" | python -c "import sys,json;print(json.load(sys.stdin).get('data'))")
[ "$code" = "0" ] && [ "$deleted" -ge 1 ] 2>/dev/null && ok "管理员触发清理，删除 $deleted 条" || bad "管理员清理接口" "$resp"
expired=$(sql "SELECT COUNT(*) FROM audit_log WHERE user_id='retention-test' AND operation='RETENTION_TEST_EXPIRED'")
kept=$(sql "SELECT COUNT(*) FROM audit_log WHERE user_id='retention-test' AND operation='RETENTION_TEST_KEEP'")
[ "$expired" = "0" ] && ok "一年前记录已删除" || bad "一年前记录未删除" "$expired"
[ "$kept" = "1" ] && ok "一年内记录保留" || bad "一年内记录被误删" "$kept"

# 3. 非管理员不能触发清理
code=$(curl -s -X POST -H "Authorization: Bearer $RT" "$BASE/api/v1/audit-logs/cleanup" | jcode)
[ "$code" = "40301" ] && ok "阅知人触发清理被拒绝" || bad "越权清理未拦截" "$code"

# 4. 清理接口幂等：再次调用不应报错
code=$(curl -s -X POST -H "Authorization: Bearer $AT" "$BASE/api/v1/audit-logs/cleanup" | jcode)
[ "$code" = "0" ] && ok "重复清理幂等" || bad "重复清理报错" "$code"

cleanup_test_rows
echo ""
echo "$PASS/$((PASS+FAIL)) 通过"
[ "$FAIL" -eq 0 ]
