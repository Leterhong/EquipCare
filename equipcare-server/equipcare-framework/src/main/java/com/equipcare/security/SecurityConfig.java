package com.equipcare.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置：
 * <ul>
 *   <li>无状态会话 + JWT 过滤器</li>
 *   <li>关闭 CSRF（前后端分离 + 无 Cookie 会话）</li>
 *   <li>方法级权限：{@code @PreAuthorize("hasAuthority('xxx')")}</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 是否在网关层放行 Swagger / OpenAPI（生产 profile 通过 application-prod.yml 关闭） */
    @org.springframework.beans.factory.annotation.Value("${equipcare.apidocs.enabled:true}")
    private boolean apidocsEnabled;

    /** 与业务无关的固定放行路径 */
    private static final String[] BASE_WHITE_LIST = {
            "/api/auth/login",
            "/api/auth/refresh",
            // SSE 长连接：浏览器 EventSource 无法携带 Authorization 头，
            // 以 30 秒一次性票据（POST /api/sse/ticket 换取）作为凭证
            "/api/sse/stream",
            "/actuator/health",
            "/actuator/info",
            "/error",
            // 前端静态页面：页面本身不含数据，所有数据接口仍需登录 + 权限校验
            "/",
            "/index.html",
            "/app.html",
            "/favicon.ico",
            "/css/**",
            "/js/**",
            "/assets/**",
            // 扫码落地页：未登录时页面自身会跳转登录页，再回到扫码结果
            "/m/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsServiceImpl userDetailsService,
                                                               PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // 保留原始异常，便于区分"用户不存在"和"密码错误"
        provider.setHideUserNotFoundExceptions(false);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> {
                    var chain = auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    if (apidocsEnabled) {
                        // 开发 / 测试环境放行接口文档；生产环境由 apidocs.enabled=false 收紧
                        chain = chain.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    chain.requestMatchers(BASE_WHITE_LIST).permitAll()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
