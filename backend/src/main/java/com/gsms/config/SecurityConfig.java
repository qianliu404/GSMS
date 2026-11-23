package com.gsms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsms.common.ErrorCode;
import com.gsms.common.R;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * 安全配置。Spring Security 5.7 起弃用 WebSecurityConfigurerAdapter，改用 SecurityFilterChain Bean。
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final GsmsProperties properties;

    public SecurityConfig(JwtTokenProvider tokenProvider, ObjectMapper objectMapper,
                          GsmsProperties properties) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .cors().configurationSource(corsConfigurationSource())
                .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/api/v1/auth/login", "/actuator/health", "/actuator/info").permitAll()
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
                .and()
                .exceptionHandling()
                .authenticationEntryPoint((request, response, ex) -> writeJson(response, ErrorCode.UNAUTHORIZED))
                .accessDeniedHandler((request, response, ex) -> writeJson(response, ErrorCode.FORBIDDEN))
                .and()
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        http.headers().frameOptions().sameOrigin();
        return http.build();
    }

    private void writeJson(HttpServletResponse response, ErrorCode code) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(code)));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // 按请求构建配置：同主机来源（Origin 的 host 与请求 Host 一致）一律放行。
        // 背景：经 Nginx 转发后，浏览器同源 POST 仍带 Origin（如 http://127.0.0.1:8081），
        // 而 Host 头经 proxy_set_header 为 $host（不含端口）或任意域名/IP，
        // 仅靠固定白名单必然漏判（换 IP、换端口、用域名访问都会 403）。前端走 Bearer
        // 令牌、不使用 Cookie（allowCredentials=false），放行同主机来源无凭证被窃风险。
        return request -> {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(properties.getWeb().getAllowedOrigins());
            if (!properties.getWeb().getAllowedOriginPatterns().isEmpty()) {
                config.setAllowedOriginPatterns(properties.getWeb().getAllowedOriginPatterns());
            }
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (origin != null && isSameHost(origin, request.getHeader(HttpHeaders.HOST))) {
                config.addAllowedOrigin(origin);
            }
            config.setAllowedMethods(
                    java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
            config.setAllowedHeaders(Collections.singletonList("*"));
            config.setExposedHeaders(java.util.Arrays.asList("Content-Disposition", "X-Export-By"));
            config.setAllowCredentials(false);
            config.setMaxAge(3600L);
            return config;
        };
    }

    /**
     * 判断 Origin 与请求 Host 是否同一主机（忽略端口与协议）。
     * 攻击者无法伪造 Origin 为受害者站点，因此该判定不会放宽跨站限制。
     */
    private boolean isSameHost(String origin, String host) {
        if (host == null) {
            return false;
        }
        try {
            java.net.URI uri = java.net.URI.create(origin);
            String originHost = uri.getHost();
            String hostOnly = host.split(":")[0];
            return originHost != null && originHost.equalsIgnoreCase(hostOnly);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
