package com.gsms.config;

import com.gsms.common.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 生成与解析。密钥取自 gsms.jwt.secret，长度不足 32 字节时启动即报错，避免弱密钥上线。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_DEPT = "dept";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMS = "perms";
    private static final String CLAIM_SCOPE = "scope";

    private final SecretKey key;
    private final long expireMillis;

    public JwtTokenProvider(GsmsProperties properties) {
        String secret = properties.getJwt().getSecret();
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("gsms.jwt.secret 长度不足 32 字节，请通过 JWT_SECRET 环境变量配置强密钥");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMillis = properties.getJwt().getExpireMinutes() * 60 * 1000;
    }

    public String generate(LoginUser user) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(user.getUserId())
                .claim(CLAIM_UID, user.getId())
                .claim(CLAIM_NAME, user.getUserName())
                .claim(CLAIM_DEPT, user.getDeptId())
                .claim(CLAIM_ROLES, user.getRoles())
                .claim(CLAIM_PERMS, user.getPermissions())
                .claim(CLAIM_SCOPE, user.getDataScope())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @SuppressWarnings("unchecked")
    public LoginUser parse(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        LoginUser user = new LoginUser();
        user.setUserId(claims.getSubject());
        Object uid = claims.get(CLAIM_UID);
        user.setId(uid == null ? null : Long.valueOf(uid.toString()));
        user.setUserName(claims.get(CLAIM_NAME, String.class));
        Object dept = claims.get(CLAIM_DEPT);
        user.setDeptId(dept == null ? null : Long.valueOf(dept.toString()));
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        if (roles != null) {
            user.setRoles(roles);
        }
        List<String> perms = claims.get(CLAIM_PERMS, List.class);
        if (perms != null) {
            user.setPermissions(perms);
        }
        user.setDataScope(claims.get(CLAIM_SCOPE, String.class));
        return user;
    }

    public long getExpireSeconds() {
        return expireMillis / 1000;
    }
}
