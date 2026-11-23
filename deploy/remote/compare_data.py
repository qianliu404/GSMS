"""对比本机与远端每张表的行数及关键中文字段指纹（HEX）。"""
import os
import subprocess

LOCAL = [r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe", "-uroot", f"-p{os.environ.get('MYSQL_ROOT_PASSWORD', '')}", "-h127.0.0.1"]
TABLES = """audit_log case_arbitration_ext case_document case_injury_ext case_main case_member
case_relation case_status_log countersign deadline_reminder notification sys_dept sys_dict
sys_menu sys_permission sys_role sys_role_menu sys_role_permission sys_user sys_user_role
task wf_instance wf_node_def""".split()

sql = " UNION ALL ".join(f"SELECT '{t}' t, COUNT(*) c FROM {t}" for t in TABLES)
sql = f"SELECT GROUP_CONCAT(CONCAT(t,':',c) ORDER BY t SEPARATOR ' ') FROM ({sql}) x;"

proc = subprocess.run(LOCAL + ["-N", "--default-character-set=utf8mb4", "case_system", "-e", sql],
                      capture_output=True, text=True)
print("LOCAL", proc.stdout.strip() or proc.stderr[:200])

# 关键中文字段指纹
fp_sql = ("SELECT CONCAT("
          "(SELECT MD5(GROUP_CONCAT(case_no,case_name,employee_name ORDER BY id)) FROM case_main),',',"
          "(SELECT MD5(GROUP_CONCAT(user_id,user_name ORDER BY id)) FROM sys_user),',',"
          "(SELECT MD5(GROUP_CONCAT(role_code,role_name ORDER BY id)) FROM sys_role),',',"
          "(SELECT MD5(GROUP_CONCAT(node_name ORDER BY id)) FROM wf_node_def),',',"
          "(SELECT MD5(GROUP_CONCAT(menu_code,menu_name ORDER BY id)) FROM sys_menu))")
fp = subprocess.run(LOCAL + ["-N", "--default-character-set=utf8mb4", "case_system", "-e", fp_sql],
                    capture_output=True, text=True)
print("LOCAL_FP", fp.stdout.strip() or fp.stderr[:200])
