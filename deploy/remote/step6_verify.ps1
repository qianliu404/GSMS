$ErrorActionPreference = 'Stop'
$api = 'http://127.0.0.1:8081/api/v1'
$body = '{"userId":"admin","password":"' + $env:GSMS_PASSWORD + '"}'
$login = Invoke-RestMethod -Uri "$api/auth/login" -Method POST -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($body))
$token = $login.data.token
$h = @{ Authorization = "Bearer $token" }

$me = Invoke-RestMethod -Uri "$api/users/me" -Headers $h
"login_user=$($me.data.userName) roles=$($me.data.roles -join ',') perms=$(@($me.data.permissions).Count)"

$cases = Invoke-RestMethod -Uri "$api/cases?page=1&size=20" -Headers $h
"cases_total=$($cases.data.total)"
foreach ($c in $cases.data.records) { "  case=$($c.caseNo) name=$($c.caseName) status=$($c.status)" }

$users = Invoke-RestMethod -Uri "$api/users?page=1&size=50" -Headers $h
"users_total=$($users.data.total)"
foreach ($u in $users.data.records) { "  user=$($u.userId) $($u.userName)" }

$roles = Invoke-RestMethod -Uri "$api/roles" -Headers $h
"roles_total=$(@($roles.data).Count)"
foreach ($r in $roles.data) { "  role=$($r.id) $($r.roleCode) $($r.roleName)" }

$pfx = Invoke-RestMethod -Uri "$api/permissions" -Headers $h
"permissions_total=$(@($pfx.data).Count) modules=$(@($pfx.data | Group-Object moduleCode).Count)"

$logs = Invoke-RestMethod -Uri "$api/audit-logs?page=1&size=1" -Headers $h
"audit_total=$($logs.data.total)"

$docs = Invoke-RestMethod -Uri "$api/cases/350/documents" -Headers $h
"case350_docs=$(@($docs.data).Count)"
foreach ($d in $docs.data) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri "$api/documents/$($d.id)/download" -Headers $h -TimeoutSec 30
        "  download=$($d.docName) http=$($r.StatusCode) bytes=$($r.RawContentLength)"
        break
    } catch { "  download=$($d.docName) err=$($_.Exception.Message)" }
}
