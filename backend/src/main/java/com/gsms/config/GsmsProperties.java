package com.gsms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "gsms")
public class GsmsProperties {

    private Jwt jwt = new Jwt();
    private Storage storage = new Storage();
    private Doc doc = new Doc();
    private Crypto crypto = new Crypto();
    private Web web = new Web();

    @Data
    public static class Web {
        /**
         * 允许跨域的来源（精确匹配，支持 https://host:port）。
         * 生产由 Nginx 同源转发 /api，正常无需跨域；开发期 Vite 走 proxy 同样同源，
         * 因此默认只放行本机调试地址，不再使用通配符。
         */
        private List<String> allowedOrigins = Arrays.asList(
                "http://127.0.0.1:5173", "http://localhost:5173",
                "http://127.0.0.1:4173", "http://localhost:4173",
                // Nginx 正式入口：同源 POST 也会带 Origin，不在白名单会被 CORS 过滤器拒成 403
                "http://127.0.0.1:8080", "http://localhost:8080",
                "https://127.0.0.1:8443", "https://localhost:8443");

        /**
         * 允许的来源通配（内网部署用）。
         * 局域网通过 http://<本机内网IP>:8080 访问时，浏览器同样会带 Origin，
         * 内网 IP 随 DHCP 变化，逐个写死不可维护，故按网段放行；环回地址
         * （服务器本机用 127.0.0.1:8081 访问）端口不固定，按任意端口放行。
         * 由于前端走 Authorization: Bearer、不依赖 Cookie（allowCredentials=false），
         * 放行内网来源不会造成凭证被第三方站点窃取。生产可用 CORS_ORIGIN_PATTERNS 覆盖。
         */
        private List<String> allowedOriginPatterns = Arrays.asList(
                "http://127.0.0.1:*", "https://127.0.0.1:*",
                "http://localhost:*", "https://localhost:*",
                "http://[::1]:*", "https://[::1]:*",
                "http://192.168.*:*", "https://192.168.*:*",
                "http://10.*:*", "https://10.*:*",
                "http://172.16.*:*", "http://172.17.*:*", "http://172.18.*:*", "http://172.19.*:*",
                "http://172.2*.*:*", "http://172.30.*:*", "http://172.31.*:*");
    }

    @Data
    public static class Crypto {
        /** 敏感字段加密密钥，长度不少于 16 字符；生产必须通过 CRYPTO_SECRET 覆盖 */
        private String secret = "gsms-local-crypto-secret-change-in-production";
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expireMinutes = 480;
    }

    @Data
    public static class Storage {
        private String root;
        private String temp;
    }

    @Data
    public static class Doc {
        private long maxFileSizeMb = 100;
        private List<String> allowedExtensions = Arrays.asList(
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg", "jpeg", "png", "zip", "rar");
    }
}
