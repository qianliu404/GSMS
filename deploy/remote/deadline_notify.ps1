param([switch]$DryRun, [switch]$DumpPayload)
# Deadline alerts -> WeCom group robot. Config: C:/GSMS/config/deadline-notify.json
$ErrorActionPreference = 'Continue'

$configPath = 'C:/GSMS/config/deadline-notify.json'
if (-not (Test-Path $configPath)) { Write-Output "CONFIG_MISSING: $configPath"; exit 2 }
$cfg = Get-Content $configPath -Raw -Encoding UTF8 | ConvertFrom-Json

$mysql = 'C:/Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe'
$env:MYSQL_PWD = $cfg.dbPassword
$days = [int]$cfg.days

$sql = @"
SELECT c.id, c.case_no, HEX(c.case_name), HEX(d.node_name), d.deadline_date,
       DATEDIFF(d.deadline_date, CURDATE()) AS days_left,
       d.status, IFNULL(d.handler_id,'') AS handler_id
FROM case_main c
JOIN deadline_reminder d ON d.case_id = c.id AND d.node_code = c.status
WHERE c.status <> 'CLOSED'
  AND d.status <> 'DONE'
  AND DATEDIFF(d.deadline_date, CURDATE()) < $days
ORDER BY d.deadline_date
"@

$raw = & $mysql -uroot --default-character-set=utf8mb4 -B -N case_system -e $sql 2>$null
if ($LASTEXITCODE -ne 0) { Write-Output "SQL_ERROR exit=$LASTEXITCODE"; exit 3 }

function Hex-ToText([string]$hx) {
    if (-not $hx -or $hx -eq 'NULL') { return '' }
    $byteArr = New-Object byte[] ($hx.Length / 2)
    for ($k = 0; $k -lt $byteArr.Length; $k++) {
        $byteArr[$k] = [Convert]::ToByte($hx.Substring($k * 2, 2), 16)
    }
    return [System.Text.Encoding]::UTF8.GetString($byteArr)
}

$rows = @()
foreach ($line in $raw) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $p = $line -split "`t"
    if ($p.Count -lt 8) { continue }
    $rows += [pscustomobject]@{
        CaseNo   = $p[1]
        CaseName = Hex-ToText $p[2]
        Node     = Hex-ToText $p[3]
        Deadline = $p[4]
        DaysLeft = [int]$p[5]
        Status   = $p[6]
        Handler  = $p[7]
    }
}

$today = Get-Date -Format 'yyyy-MM-dd HH:mm'
$h = '7b227469746c65223a2022e6a188e4bbb6e69c9fe99990e4b8b4e69c9fe68f90e98692222c202274696d65223a2022e7bb9fe8aea1e697b6e997b4222c202272756c65223a2022e8a784e58899efbc9ae69caae7bb93e6a188202b20e5bd93e5898de88a82e782b9e69caae5ae8ce68890e69c9fe99990202b20e589a9e4bd99e4b88de8b6b3207b647d20e5a4a9efbc88e590abe5b7b2e8b685e69c9fefbc89222c20226e6f6e65223a2022e5bd93e5898de697a0e7aca6e59088e69da1e4bbb6e79a84e6a188e4bbb6e38082222c2022746f74616c223a2022e585b1207b6e7d20e4bbb6efbc9a222c20226f766572647565223a2022e5b7b2e8b685e69c9f207b6e7d20e5a4a9222c2022746f646179223a2022e4bb8ae697a5e588b0e69c9f222c20226c656674223a2022e589a9e4bd99207b6e7d20e5a4a9222c20226e6f48616e646c6572223a2022e69caae68c87e5ae9a222c20226e6f6465223a2022e5bd93e5898de88a82e782b9222c2022646561646c696e65223a2022e688aae6ada2e697a5e69c9f222c202268616e646c6572223a2022e58a9ee79086e4baba227d'
$hb = New-Object byte[] ($h.Length / 2)
for ($k = 0; $k -lt $hb.Length; $k++) { $hb[$k] = [Convert]::ToByte($h.Substring($k * 2, 2), 16) }
$tpl = [System.Text.Encoding]::UTF8.GetString($hb) | ConvertFrom-Json

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('## ' + $tpl.title)
$lines.Add('> ' + $tpl.time + ':' + $today)
$lines.Add('> ' + ($tpl.rule -replace '\{d\}', $days))
$lines.Add('')
if ($rows.Count -eq 0) {
    $lines.Add($tpl.none)
} else {
    $lines.Add('**' + ($tpl.total -replace '\{n\}', $rows.Count) + '**')
    $lines.Add('')
    $i = 0
    foreach ($r in $rows) {
        $i++
        if ($r.DaysLeft -lt 0) {
            $flag = $tpl.overdue -replace '\{n\}', [math]::Abs($r.DaysLeft)
        } elseif ($r.DaysLeft -eq 0) {
            $flag = $tpl.today
        } else {
            $flag = $tpl.left -replace '\{n\}', $r.DaysLeft
        }
        if ($r.Handler) { $handler = $r.Handler } else { $handler = $tpl.noHandler }
        $lines.Add('**' + $i + '. ' + $r.CaseNo + ' ' + $r.CaseName + '**')
        $lines.Add('> ' + $tpl.node + ':' + $r.Node + '  |  ' + $tpl.deadline + ':' + $r.Deadline + '  |  ' + $flag + '  |  ' + $tpl.handler + ':' + $handler)
        $lines.Add('')
    }
}
$content = $lines -join "`n"
$payloadJson = @{ msgtype = 'markdown'; markdown = @{ content = $content } } | ConvertTo-Json -Depth 5 -Compress
$bytes = [System.Text.Encoding]::UTF8.GetBytes($payloadJson)
if ($DumpPayload) { [System.IO.File]::WriteAllBytes('C:/GSMS/logs/deadline-payload.json', $bytes) }

Write-Output '=== MATCHED=' + $rows.Count + ' ==='
if ($DryRun) { Write-Output 'DRYRUN=1 (not sent)'; exit 0 }

try {
    $resp = Invoke-RestMethod -Uri $cfg.webhook -Method Post -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec 20
    Write-Output ("SEND_RESULT errcode=" + $resp.errcode + " errmsg=" + $resp.errmsg)
} catch {
    Write-Output ('SEND_ERROR ' + $_.Exception.Message)
    exit 4
}
