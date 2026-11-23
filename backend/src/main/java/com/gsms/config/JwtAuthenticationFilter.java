package com.gsms.config;

import com.gsms.common.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器。解析失败时不抛异常，交由 entryPoint 返回 40101，
 * 避免未登录请求被 500 兜底淹没真实原因。
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            try {
                LoginUser user = tokenProvider.parse(token);
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (user.getRoles() != null) {
                    for (String role : user.getRoles()) {
                        authorities.add(new SimpleGrantedAuthority(role));
                    }
                }
                // 细粒度权限码与角色同为 authority，@PreAuthorize 统一用 hasAuthority
                if (user.getPermissions() != null) {
                    for (String perm : user.getPermissions()) {
                        authorities.add(new SimpleGrantedAuthority(perm));
                    }
                }
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // 令牌过期或非法：保持匿名，由 EntryPoint 统一返回 40101
                SecurityContextHolder.clearContext();
                log.debug("JWT 解析失败: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
