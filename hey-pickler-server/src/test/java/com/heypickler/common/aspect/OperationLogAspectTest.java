package com.heypickler.common.aspect;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.entity.OperationLog;
import com.heypickler.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationLogAspectTest {

    private OperationLogService service;
    private OperationLogAspect aspect;
    private final List<OperationLog> captured = new ArrayList<>();

    @BeforeEach
    void setUp() {
        captured.clear();
        service = mock(OperationLogService.class);
        doAnswer(inv -> { captured.add(inv.getArgument(0)); return null; })
            .when(service).record(any(OperationLog.class));
        aspect = new OperationLogAspect(service, new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest(MockHttpServletRequest req) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    private ProceedingJoinPoint mockJoinPoint(Object... args) throws Exception {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(args);
        MethodSignature sig = mock(MethodSignature.class);
        java.lang.reflect.Method m = OperationLogAspectTest.class
                .getDeclaredMethod("dummyControllerMethod");
        when(sig.getMethod()).thenReturn(m);
        when(pjp.getSignature()).thenReturn(sig);
        return pjp;
    }

    // used only to populate MethodSignature mock — content irrelevant
    void dummyControllerMethod() {}

    // --- Tests ---

    @Test
    void success_recordsStatus1() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/users");
        req.setAttribute("adminId", 7L);
        req.setAttribute("adminRole", "SUPER_ADMIN");
        req.setRemoteAddr("10.0.0.1");
        bindRequest(req);

        ProceedingJoinPoint pjp = mockJoinPoint("{name=alice}");
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.around(pjp);

        assertEquals("ok", result);
        assertEquals(1, captured.size());
        OperationLog log = captured.get(0);
        assertEquals(7L, log.getOperatorId());
        assertEquals("SUPER_ADMIN", log.getOperatorRole());
        assertEquals("POST", log.getMethod());
        assertEquals("USER", log.getModule());
        assertEquals("CREATE", log.getAction());
        assertEquals(1, log.getStatus());
        assertNull(log.getErrorCode());
        assertTrue(log.getLatencyMs() >= 0);
    }

    @Test
    void bizException_recordsStatus0AndCode() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/api/admin/users/42");
        req.setAttribute("adminId", 1L);
        req.setAttribute("adminRole", "ADMIN");
        bindRequest(req);

        ProceedingJoinPoint pjp = mockJoinPoint();
        BizException be = new BizException(ErrorCode.PARAM_ERROR);
        when(pjp.proceed()).thenThrow(be);

        BizException thrown = assertThrows(BizException.class, () -> aspect.around(pjp));
        assertSame(be, thrown);

        assertEquals(1, captured.size());
        OperationLog log = captured.get(0);
        assertEquals(0, log.getStatus());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), log.getErrorCode());
        assertEquals("UPDATE", log.getAction());
        assertEquals("42", log.getTargetId());
    }

    @Test
    void runtimeException_recordsStatus0And500() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/api/admin/banners/9");
        req.setAttribute("adminId", 2L);
        req.setAttribute("adminRole", "ADMIN");
        bindRequest(req);

        ProceedingJoinPoint pjp = mockJoinPoint();
        when(pjp.proceed()).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> aspect.around(pjp));

        assertEquals(1, captured.size());
        OperationLog log = captured.get(0);
        assertEquals(0, log.getStatus());
        assertEquals(500, log.getErrorCode());
        assertEquals("boom", log.getErrorMsg());
    }

    @Test
    void getMethod_skipped() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/users");
        bindRequest(req);
        ProceedingJoinPoint pjp = mockJoinPoint();
        when(pjp.proceed()).thenReturn("list");

        Object result = aspect.around(pjp);
        assertEquals("list", result);
        assertEquals(0, captured.size(), "GET requests must not be audited");
    }

    @Test
    void loginEndpoint_recordsAnonymousOperator() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/auth/login");
        // adminId/adminRole not set (AdminAuthFilter skips this path)
        bindRequest(req);

        Map<String, Object> loginBody = new HashMap<>();
        loginBody.put("username", "admin");
        loginBody.put("password", "admin123");
        ProceedingJoinPoint pjp = mockJoinPoint(loginBody);
        when(pjp.proceed()).thenReturn("token");

        aspect.around(pjp);

        assertEquals(1, captured.size());
        OperationLog log = captured.get(0);
        assertNull(log.getOperatorId());
        assertEquals("ANONYMOUS", log.getOperatorRole());
        assertEquals("AUTH", log.getModule());
        assertEquals("LOGIN", log.getAction());
        // Password must be masked
        assertNotNull(log.getParams());
        assertTrue(log.getParams().contains("\"password\":\"***\""),
            "password should be masked, got: " + log.getParams());
        assertFalse(log.getParams().contains("admin123"));
    }

    @Test
    void auditWriteFailure_doesNotPropagate() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/banners");
        req.setAttribute("adminId", 1L);
        req.setAttribute("adminRole", "ADMIN");
        bindRequest(req);

        // Make service throw — aspect should swallow
        doThrow(new RuntimeException("db down")).when(service).record(any());
        ProceedingJoinPoint pjp = mockJoinPoint();
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.around(pjp);
        assertEquals("ok", result, "audit failure must not break the request");
    }

    @Test
    void xForwardedFor_firstHopUsed() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/banners");
        req.setAttribute("adminId", 1L);
        req.setAttribute("adminRole", "ADMIN");
        req.addHeader("X-Forwarded-For", "203.0.113.5, 70.41.3.18");
        req.setRemoteAddr("10.0.0.1");
        bindRequest(req);

        ProceedingJoinPoint pjp = mockJoinPoint();
        when(pjp.proceed()).thenReturn("ok");
        aspect.around(pjp);

        assertEquals("203.0.113.5", captured.get(0).getIp());
    }

    @Test
    void paramsTruncated_whenTooLarge() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/banners");
        req.setAttribute("adminId", 1L);
        req.setAttribute("adminRole", "ADMIN");
        bindRequest(req);

        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 5000; i++) huge.append('x');
        ProceedingJoinPoint pjp = mockJoinPoint(huge.toString());
        when(pjp.proceed()).thenReturn("ok");

        aspect.around(pjp);
        OperationLog log = captured.get(0);
        assertNotNull(log.getParams());
        assertTrue(log.getParams().length() <= 2000, "params must be truncated to 2000 chars");
    }
}
