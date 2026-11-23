"""通用远程执行工具：通过 WinRM 在目标 Windows 服务器执行 PowerShell。

用法：
  GSMS_PASS=... python exec.py "Get-Date"
  GSMS_PASS=... python exec.py --file script.ps1
凭据全部来自环境变量，不写入文件。
"""
import os
import sys

import winrm

HOST = os.environ.get("GSMS_HOST", "192.168.11.179")
USER = os.environ.get("GSMS_USER", "Administrator")
PASS = os.environ.get("GSMS_PASS")
if not PASS:
    sys.exit("缺少 GSMS_PASS")


def session():
    return winrm.Session(HOST, auth=(USER, PASS), transport="ntlm", server_cert_validation="ignore")


def run_ps(script: str, timeout: int = 300):
    s = session()
    r = s.run_ps(script)
    out = r.std_out.decode("utf-8", errors="replace")
    err = r.std_err.decode("utf-8", errors="replace")
    # 过滤 CLIXML 进度噪声
    err = "\n".join(line for line in err.splitlines() if not line.startswith("#< CLIXML") and "<Objs" not in line)
    return r.status_code, out, err.strip()


def main():
    if len(sys.argv) < 2:
        sys.exit("用法: exec.py \"<powershell>\" | exec.py --file <path>")
    if sys.argv[1] == "--file":
        script = open(sys.argv[2], encoding="utf-8").read()
    else:
        script = sys.argv[1]
    code, out, err = run_ps(script)
    print(out)
    if err:
        print("[stderr]", err[:2000])
    print(f"[exit={code}]")
    sys.exit(0 if code == 0 else 1)


if __name__ == "__main__":
    main()
