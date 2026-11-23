package com.gsms.module.audit.service;

import com.gsms.common.LoginUser;
import com.gsms.common.UserContext;
import com.gsms.module.audit.entity.AuditLog;
import com.gsms.module.audit.mapper.AuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 审计记录。所有写操作与敏感读操作均需落库，超过保留期（默认 1 年）由
 * {@link AuditRetentionService} 自动清理。
 * 参数入库前统一脱敏：身份证、手机号、密码、金额。
 */
@Slf4j
@Service
public class AuditService {

    private static final Pattern ID_CARD = Pattern.compile("(\\d{6})\\d{8}(\\d{3}[0-9Xx])");
    private static final Pattern MOBILE = Pattern.compile("(1[3-9]\\d)\\d{8}");
    private static final Pattern PASSWORD = Pattern.compile("(\"(?:password|passwordHash|oldPassword|newPassword)\"\\s*:\\s*\")[^\"]*(\")");

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void success(String module, String operation, String businessId, String params) {
        record(module, operation, businessId, "SUCCESS", params);
    }

    public void fail(String module, String operation, String businessId, String reason) {
        record(module, operation, businessId, "FAIL", reason);
    }

    public void record(String module, String operation, String businessId, String result, String params) {
        try {
            AuditLog entity = new AuditLog();
            LoginUser user = UserContext.getOrNull();
            entity.setUserId(user == null ? "anonymous" : user.getUserId());
            entity.setUserName(user == null ? "anonymous" : user.getUserName());
            entity.setModule(module);
            entity.setOperation(operation);
            entity.setBusinessId(businessId);
            entity.setResult(result);
            entity.setRequestParams(mask(params));
            entity.setRequestIp(currentIp());
            entity.setTraceId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            entity.setOperateTime(LocalDateTime.now());
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            // 审计失败不得影响主业务，但必须留痕到本地日志
            log.error("审计日志写入失败: module={}, operation={}, bizId={}", module, operation, businessId, e);
        }
    }

    /** 敏感信息脱敏，避免审计表成为新的泄露源 */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = ID_CARD.matcher(text).replaceAll("$1********$2");
        masked = MOBILE.matcher(masked).replaceAll("$1****");
        masked = PASSWORD.matcher(masked).replaceAll("$1******$2");
        return masked.length() > 4000 ? masked.substring(0, 4000) : masked;
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
