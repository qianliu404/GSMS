package com.gsms;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 一次性工具：生成初始账号的 BCrypt 哈希，输出后粘贴到 sql/init.sql。
 * 仅用于初始化，不参与运行期逻辑。
 */
public class PasswordHashGen {

    public static void main(String[] args) {
        String raw = args.length > 0 ? args[0] : System.getenv().getOrDefault("GSMS_PASSWORD", "");
        String[] accounts = {"admin", "caseadmin", "depthead", "handler",
                "cohandler", "signer", "reader", "auditor"};
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println("RAW_PASSWORD=" + raw);
        for (String account : accounts) {
            System.out.println(account + "\t" + encoder.encode(raw));
        }
        // 自校验：确保生成的哈希可被同一编码器验证通过
        String sample = encoder.encode(raw);
        System.out.println("VERIFY_SELF=" + encoder.matches(raw, sample));
    }
}
