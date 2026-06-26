package com.heypickler.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.Result;
import com.heypickler.common.util.JwtUtil;
import com.heypickler.mapper.UserMapper;
import com.heypickler.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AppAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;

    private static final Set<String> PUBLIC_GET_PREFIXES = Set.of(
            "/api/app/banners",
            "/api/app/events",
            "/api/app/rankings"
    );

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/app/auth/login",
            "/api/app/auth/phone",
            "/api/app/auth/refresh"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/app/")) return true;

        if (PUBLIC_PATHS.contains(path)) return true;

        if ("GET".equals(request.getMethod()) && PUBLIC_GET_PREFIXES.stream().anyMatch(path::startsWith)) {
            // my-team is user-scoped: it must run through the auth filter to bind the
            // caller's userId, even though it lives under the public /api/app/events prefix
            // (event list/detail/results stay anonymously browsable).
            if (path.endsWith("/my-team")) {
                return false;
            }
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
        if (!"app".equals(type)) {
            writeError(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        Long userId = jwtUtil.getUserId(token);

        User user = userMapper.selectById(userId);
        if (user == null || "BANNED".equals(user.getStatus())) {
            writeError(response, ErrorCode.USER_BANNED);
            return;
        }

        request.setAttribute("userId", userId);
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
        response.setContentType("application/json;charset=UTF-8");
        int status = errorCode == ErrorCode.USER_BANNED ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED;
        response.setStatus(status);
        objectMapper.writeValue(response.getOutputStream(), Result.fail(errorCode.getCode(), errorCode.getMessage()));
    }
}
