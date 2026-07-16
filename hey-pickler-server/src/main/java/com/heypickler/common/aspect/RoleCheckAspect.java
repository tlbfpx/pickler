package com.heypickler.common.aspect;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
public class RoleCheckAspect {

    @Before("@annotation(com.heypickler.common.annotation.RequireRole)")
    public void checkRole(JoinPoint joinPoint) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            // 非请求上下文（@Async / 定时 / 进程内调用）触达 @RequireRole 方法 → fail-closed，
            // 拒绝执行（否则等于跳过角色校验的提权地雷）
            throw new BizException(ErrorCode.UNAUTHORIZED, "无法校验角色（非 HTTP 请求上下文）");
        }

        HttpServletRequest request = attrs.getRequest();
        String role = (String) request.getAttribute("adminRole");
        if (role == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        Set<String> allowedRoles = resolveAllowedRoles(joinPoint);
        if (!allowedRoles.contains(role)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private Set<String> resolveAllowedRoles(JoinPoint joinPoint) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        java.lang.reflect.Method method = sig.getMethod();

        RequireRole requireRole = method.getAnnotation(RequireRole.class);
        if (requireRole != null) {
            return Arrays.stream(requireRole.value())
                    .map(UserRole::name)
                    .collect(Collectors.toSet());
        }

        return Set.of("SUPER_ADMIN", "ADMIN");
    }
}
