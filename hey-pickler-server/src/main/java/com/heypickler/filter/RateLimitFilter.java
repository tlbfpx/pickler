package com.heypickler.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${hey-pickler.rate-limit.login:10}")
    private int loginRateLimit;

    @Value("${hey-pickler.rate-limit.admin:120}")
    private int adminRateLimit;

    @Value("${hey-pickler.rate-limit.admin-anon:30}")
    private int adminAnonRateLimit;

    @Value("${hey-pickler.rate-limit.default:60}")
    private int defaultRateLimit;

    private static final String LUA_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
            "if count == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return count";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            LUA_SCRIPT, Long.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String key;
        int maxRequests;
        int windowSeconds = 60;

        String clientIp = resolveClientIp(request);

        if (isLoginEndpoint(path)) {
            key = "heypickler:ratelimit:login:" + clientIp;
            maxRequests = loginRateLimit;
            windowSeconds = 60;
        } else if (path.startsWith("/api/admin/")) {
            Object adminIdAttr = request.getAttribute("adminId");
            if (adminIdAttr != null) {
                key = "heypickler:ratelimit:admin:" + adminIdAttr;
                maxRequests = adminRateLimit;
            } else {
                key = "heypickler:ratelimit:admin:anon:" + clientIp;
                maxRequests = adminAnonRateLimit;
            }
        } else {
            key = "heypickler:ratelimit:" + clientIp;
            maxRequests = defaultRateLimit;
        }

        if (!tryAcquire(key, maxRequests, windowSeconds)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(429);
            objectMapper.writeValue(response.getOutputStream(),
                    Result.fail(ErrorCode.RATE_LIMITED.getCode(), ErrorCode.RATE_LIMITED.getMessage()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLoginEndpoint(String path) {
        return "/api/admin/auth/login".equals(path) || "/api/app/auth/login".equals(path) || "/api/app/auth/phone".equals(path);
    }

    private boolean tryAcquire(String key, int maxRequests, int windowSeconds) {
        Long count = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(windowSeconds)
        );
        return count != null && count <= maxRequests;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
