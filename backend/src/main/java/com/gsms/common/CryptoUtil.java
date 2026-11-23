package com.gsms.common;

import com.gsms.config.GsmsProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段加解密与脱敏。
 * 存储：身份证号、待遇/裁决金额使用 AES-256-GCM，密文格式 Base64(iv + cipherText)。
 * 展示：按角色调用 maskXxx，审计日志同样记录脱敏值。
 */
@Component
public class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public CryptoUtil(GsmsProperties properties) {
        String secret = properties.getCrypto().getSecret();
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("gsms.crypto.secret 未配置或过短，敏感字段无法安全存储");
        }
        try {
            // 通过 SHA-256 派生固定长度密钥，避免直接截断弱密钥
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.keySpec = new SecretKeySpec(digest, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("初始化加密密钥失败", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherText, 0, result, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "敏感字段加密失败");
        }
    }

    public String decrypt(String cipherTextBase64) {
        if (cipherTextBase64 == null || cipherTextBase64.isEmpty()) {
            return cipherTextBase64;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(cipherTextBase64);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(raw, IV_LENGTH, raw.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 历史明文数据或密钥变更时不应导致接口 500，返回 null 由上层按无值处理
            return null;
        }
    }

    /** 姓名脱敏：张三 -> 张*；欧阳修 -> 欧** */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.substring(0, 1));
        for (int i = 1; i < name.length(); i++) {
            sb.append('*');
        }
        return sb.toString();
    }

    /** 身份证脱敏：保留前 6 位与后 4 位 */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() <= 10) {
            return idCard == null ? null : "****";
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }
}
