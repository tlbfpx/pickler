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
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * D6 fix: dual-key atomic rate limit (user + ip) via single Lua script.
 * The previous implementation used one key per scope, which meant a single
 * client could burst across IPs (or two clients sharing an IP couldn't be
 * distinguished from each other). Folding both keys into one EVAL closes
 * the gap; the script enforces "reject if either dimension exceeds max".
 */
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

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            readScript(), Long.class);

    private static String readScript() {
        try (var in = new ClassPathResource("lua/rate_limit.lua").getInputStream()) {
            return new String(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("rate_limit.lua missing from classpath", e);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String userKey = ""; // empty = no user dimension
        String ipKey;
        int maxRequests;
        int windowSeconds = 60;

        String clientIp = resolveClientIp(request);

        if (isLoginEndpoint(path)) {
            ipKey = "heypickler:ratelimit:login:" + clientIp;
            maxRequests = loginRateLimit;
            windowSeconds = 60;
        } else if (path.startsWith("/api/admin/")) {
            Object adminIdAttr = request.getAttribute("adminId");
            if (adminIdAttr != null) {
                userKey = "heypickler:ratelimit:admin:user:" + adminIdAttr;
                ipKey   = "heypickler:ratelimit:admin:ip:"   + clientIp;
                maxRequests = adminRateLimit;
            } else {
                ipKey = "heypickler:ratelimit:admin:anon:" + clientIp;
                maxRequests = adminAnonRateLimit;
            }
        } else {
            ipKey = "heypickler:ratelimit:" + clientIp;
            maxRequests = defaultRateLimit;
        }

        if (!tryAcquire(userKey, ipKey, maxRequests, windowSeconds)) {
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

    private boolean tryAcquire(String userKey, String ipKey, int maxRequests, int windowSeconds) {
        Long result = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Arrays.asList(userKey, ipKey),
                String.valueOf(maxRequests),
                String.valueOf(windowSeconds)
        );
        return result != null && result == 1L;
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
