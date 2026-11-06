package com.equipcare.security;

import com.equipcare.common.result.Result;
import com.equipcare.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证访问统一返回 401，响应体仍为 {@link Result} 结构。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object error = request.getAttribute(JwtAuthenticationFilter.ATTR_JWT_ERROR);
        ResultCode code = ResultCode.UNAUTHORIZED;
        if (JwtAuthenticationFilter.JWT_ERROR_EXPIRED.equals(error)) {
            code = ResultCode.AUTH_TOKEN_EXPIRED;
        } else if (JwtAuthenticationFilter.JWT_ERROR_INVALID.equals(error)) {
            code = ResultCode.AUTH_TOKEN_INVALID;
        }
        response.setStatus(code.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code)));
    }
}
