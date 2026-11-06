package com.equipcare.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析。兼容 Nginx 反向代理场景。
 */
public final class IpUtil {

    private static final String UNKNOWN = "unknown";
    private static final String[] HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
    };

    private IpUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        for (String header : HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !UNKNOWN.equalsIgnoreCase(value)) {
                int idx = value.indexOf(',');
                String ip = idx > 0 ? value.substring(0, idx) : value;
                return ip.trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? UNKNOWN : remote;
    }
}
