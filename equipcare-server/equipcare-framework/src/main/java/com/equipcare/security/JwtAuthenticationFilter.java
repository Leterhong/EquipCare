package com.equipcare.security;
import com.equipcare.common.security.LoginUser;
import com.equipcare.common.security.TokenStore;

import com.equipcare.common.constant.CommonConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * JWT 认证过滤器。
 *
 * <p>认证失败时不直接返回错误，而是保持匿名并记录失败原因，
 * 由 {@link RestAuthenticationEntryPoint} 统一输出 401，保证响应结构一致。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** 请求属性：令牌校验失败原因，供入口点区分提示 */
    public static final String ATTR_JWT_ERROR = "equipcare.jwt.error";
    public static final String JWT_ERROR_EXPIRED = "EXPIRED";
    public static final String JWT_ERROR_INVALID = "INVALID";

    private final JwtTokenProvider tokenProvider;
    private final TokenStore tokenStore;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, TokenStore tokenStore,
                                   UserDetailsServiceImpl userDetailsService) {
        this.tokenProvider = tokenProvider;
        this.tokenStore = tokenStore;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(CommonConstants.HEADER_AUTHORIZATION);
        if (header == null || !header.startsWith(CommonConstants.BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(CommonConstants.BEARER_PREFIX.length()).trim();
        try {
            Claims claims = tokenProvider.parse(token);
            if (!tokenProvider.isAccessToken(claims)) {
                request.setAttribute(ATTR_JWT_ERROR, JWT_ERROR_INVALID);
                chain.doFilter(request, response);
                return;
            }
            String jti = claims.getId();
            if (tokenStore.isBlacklisted(jti)) {
                request.setAttribute(ATTR_JWT_ERROR, JWT_ERROR_INVALID);
                chain.doFilter(request, response);
                return;
            }

            LoginUser loginUser = tokenStore.getLoginUser(jti);
            if (loginUser == null) {
                // Redis 不可用或会话缺失时，回源数据库重建会话（不视为未登录，不绕过权限：
                // 权限集合来自数据库实时加载，禁用用户在下方 isEnabled 检查被拦截）
                loginUser = (LoginUser) userDetailsService.loadUserByUsername(claims.getSubject());
                long remainMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
                if (remainMillis > 0) {
                    tokenStore.saveLoginUser(jti, loginUser, Duration.ofMillis(remainMillis));
                }
            }

            if (!loginUser.isEnabled()) {
                request.setAttribute(ATTR_JWT_ERROR, JWT_ERROR_INVALID);
                chain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // 多租户（第十期）：行级隔离上下文随会话建立，请求结束必须清理（线程池复用）
            com.equipcare.common.tenant.TenantContext.set(loginUser.getTenantId());
            try {
                chain.doFilter(request, response);
            } finally {
                com.equipcare.common.tenant.TenantContext.clear();
            }
            return;
        } catch (ExpiredJwtException ex) {
            request.setAttribute(ATTR_JWT_ERROR, JWT_ERROR_EXPIRED);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            log.debug("令牌校验失败: {}", ex.getMessage());
            request.setAttribute(ATTR_JWT_ERROR, JWT_ERROR_INVALID);
        }

        chain.doFilter(request, response);
    }
}
