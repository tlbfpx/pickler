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
        if (attrs == null) return;

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
