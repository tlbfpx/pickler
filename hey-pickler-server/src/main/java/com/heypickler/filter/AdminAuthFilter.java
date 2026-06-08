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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/admin/") || EXCLUDE_PATHS.contains(path);
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

        // Check Redis session
        String tokenId = String.valueOf(jwtUtil.getUserId(token));
        String sessionKey = RedisKey.adminSession(tokenId);
        if (Boolean.FALSE.equals(redisTemplate.hasKey(sessionKey))) {
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
        response.setStatus(HttpServletResponse.SC_OK);
        objectMapper.writeValue(response.getOutputStream(), Result.fail(code, message));
    }
}
