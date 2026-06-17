package com.heypickler.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heypickler.common.util.OperationLogClassifier;
import com.heypickler.common.util.SensitiveDataUtil;
import com.heypickler.entity.OperationLog;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures every write operation (POST/PUT/DELETE) under /api/admin/** into
 * operation_log. GET requests are skipped. Failed calls (BizException +
 * unexpected RuntimeException) are recorded with status=0 then rethrown so the
 * upstream error path (GlobalExceptionHandler) still produces the right HTTP
 * response. Audit writes are fire-and-forget: any persistence failure is logged
 * but never propagated, so the admin request is never broken by audit trouble.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    private static final int MAX_PARAMS_LEN = 2000;
    private static final int MAX_ERROR_MSG_LEN = 512;
    private static final int MAX_UA_LEN = 512;

    @Around("execution(* com.heypickler.controller.admin..*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        // No HTTP context (e.g., test wiring) — proceed without audit.
        if (request == null) return pjp.proceed();
        // Skip GET — audit only writes (per requirement).
        if ("GET".equalsIgnoreCase(request.getMethod())) return pjp.proceed();

        long start = System.currentTimeMillis();
        OperationLog entry = buildBaseLog(request, pjp);

        try {
            Object result = pjp.proceed();
            entry.setStatus(1);
            return result;
        } catch (BizException be) {
            entry.setStatus(0);
            entry.setErrorCode(be.getCode());
            entry.setErrorMsg(truncate(be.getMessage(), MAX_ERROR_MSG_LEN));
            throw be;
        } catch (Throwable t) {
            entry.setStatus(0);
            entry.setErrorCode(ErrorCode.INTERNAL_ERROR.getCode());
            entry.setErrorMsg(truncate(t.getMessage(), MAX_ERROR_MSG_LEN));
            throw t;
        } finally {
            entry.setLatencyMs((int) (System.currentTimeMillis() - start));
            try {
                operationLogService.record(entry);
            } catch (Exception e) {
                log.error("operation_log audit write failed for path={}", request.getRequestURI(), e);
            }
        }
    }

    private OperationLog buildBaseLog(HttpServletRequest request, ProceedingJoinPoint pjp) {
        OperationLog entry = new OperationLog();
        Object adminId = request.getAttribute("adminId");
        if (adminId instanceof Long) entry.setOperatorId((Long) adminId);
        Object role = request.getAttribute("adminRole");
        entry.setOperatorRole(role instanceof String ? (String) role : "ANONYMOUS");
        entry.setMethod(request.getMethod().toUpperCase());
        entry.setPath(request.getRequestURI());
        entry.setIp(resolveIp(request));
        entry.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_UA_LEN));

        OperationLogClassifier.Classification c =
                OperationLogClassifier.classify(request.getMethod(), request.getRequestURI());
        entry.setModule(c.module);
        entry.setAction(c.action);
        entry.setTargetType(c.targetType);
        entry.setTargetId(c.targetId);

        entry.setParams(serializeAndMaskArgs(pjp.getArgs()));
        return entry;
    }

    private String serializeAndMaskArgs(Object[] args) {
        if (args == null || args.length == 0) return null;
        // Filter out framework types — Jackson can't serialize HttpServletRequest,
        // HttpServletResponse, MultipartFile, etc. and they don't carry audit value anyway.
        List<Object> auditable = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg == null) continue;
            if (jakarta.servlet.ServletRequest.class.isInstance(arg)) continue;
            if (jakarta.servlet.ServletResponse.class.isInstance(arg)) continue;
            if (org.springframework.web.multipart.MultipartFile.class.isInstance(arg)) continue;
            auditable.add(arg);
        }
        if (auditable.isEmpty()) return null;
        try {
            Object payload = auditable.size() == 1 ? auditable.get(0) : auditable;
            String json = objectMapper.writeValueAsString(payload);
            String masked = SensitiveDataUtil.maskJson(json);
            return truncate(masked, MAX_PARAMS_LEN);
        } catch (Exception e) {
            return truncate("[unserializable: " + e.getClass().getSimpleName() + "]", MAX_PARAMS_LEN);
        }
    }

    private static String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
