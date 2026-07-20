package com.heypickler.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heypickler.common.constant.RedisKey;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.Result;
import com.heypickler.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Set<String> EXCLUDE_PATHS = Set.of(
            "/api/admin/auth/login"
    );

    /** GET-only 匿名读：login 前页要展示品牌（app 名/logo/主色），故读不鉴权；PUT 仍鉴权。 */
    private static final Set<String> PUBLIC_ADMIN_GET_PATHS = Set.of(
            "/api/admin/brand"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/admin/")) {
            return true;
        }
        if (EXCLUDE_PATHS.contains(path)) {
            return true;
        }
        if ("GET".equals(request.getMethod()) && PUBLIC_ADMIN_GET_PATHS.contains(path)) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null || !jwtUtil.validate(token)) {
            writeError(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        String type = jwtUtil.getType(token);
        if (!"admin".equals(type)) {
            writeError(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        // Check Redis session — fail-closed：仅显式 true 放行；null/Redis 异常一律拒
        // （防 Redis 抖动时 hasKey 返回 null 被当作“会话有效”绕过撤销）
        String tokenId = String.valueOf(jwtUtil.getUserId(token));
        String sessionKey = RedisKey.adminSession(tokenId);
        boolean sessionValid;
        try {
            sessionValid = Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey));
        } catch (Exception e) {
            sessionValid = false;
        }
        if (!sessionValid) {
            writeError(response, ErrorCode.UNAUTHORIZED.getCode(), "会话已失效");
            return;
        }

        Long adminId = jwtUtil.getUserId(token);
        String role = jwtUtil.getRole(token);
        request.setAttribute("adminId", adminId);
        request.setAttribute("adminRole", role);
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        writeError(response, errorCode.getCode(), errorCode.getMessage());
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        objectMapper.writeValue(response.getOutputStream(), Result.fail(code, message));
    }
}
