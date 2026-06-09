package com.heypickler.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Atomically increments the counter and sets TTL only on first increment.
     * Returns the new count value.
     * KEYS[1] = rate limit key
     * ARGV[1] = TTL in seconds
     */
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
        String key;
        int maxRequests;
        if (request.getRequestURI().startsWith("/api/admin/")) {
            String adminId = String.valueOf(request.getAttribute("adminId"));
            key = "heypickler:ratelimit:admin:" + adminId;
            maxRequests = 120;
        } else {
            key = "heypickler:ratelimit:" + request.getRemoteAddr();
            maxRequests = 60;
        }

        if (!tryAcquire(key, maxRequests)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getOutputStream(),
                    Result.fail(ErrorCode.RATE_LIMITED.getCode(), ErrorCode.RATE_LIMITED.getMessage()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean tryAcquire(String key, int maxRequests) {
        Long count = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                "60"  // 60 seconds window
        );
        return count != null && count <= maxRequests;
    }
}
