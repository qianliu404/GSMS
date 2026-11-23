"""通过 WinRM 分片上传文件到远端（base64 追加，绕开本机入站端口限制）。

用法：GSMS_PASS=... python upload.py <local_file> <remote_path>
"""
import base64
import os
import sys

import winrm

HOST = os.environ.get("GSMS_HOST", "192.168.11.179")
USER = os.environ.get("GSMS_USER", "Administrator")
PASS = os.environ.get("GSMS_PASS")
CHUNK = 30000

if not PASS:
    sys.exit("缺少 GSMS_PASS")
if len(sys.argv) < 3:
    sys.exit("用法: upload.py <local_file> <remote_path>")

local, remote = sys.argv[1], sys.argv[2]
data = base64.b64encode(open(local, "rb").read()).decode("ascii")
tmp = remote + ".b64"

s = winrm.Session(HOST, auth=(USER, PASS), transport="ntlm", server_cert_validation="ignore")


def ps(script: str):
    r = s.run_ps(script)
    if r.status_code != 0:
        raise SystemExit("远端失败: " + r.std_err.decode("utf-8", "replace")[:500])
    return r.std_out.decode("utf-8", "replace").strip()


ps(f"[IO.File]::WriteAllText('{tmp}', '')")
total = (len(data) + CHUNK - 1) // CHUNK
for i in range(total):
    part = data[i * CHUNK:(i + 1) * CHUNK]
    ps(f"[IO.File]::AppendAllText('{tmp}', '{part}', [Text.Encoding]::ASCII)")

out = ps(
    f"$b=[Convert]::FromBase64String([IO.File]::ReadAllText('{tmp}'));"
    f"[IO.File]::WriteAllBytes('{remote}', $b);"
    f"Remove-Item '{tmp}' -Force;"
    f"(Get-Item '{remote}').Length"
)
print(f"uploaded {local} -> {remote} bytes={out} chunks={total}")
