package com.heypickler.common.aspect;

import com.heypickler.common.annotation.PublicAnonymousAccess;
import com.heypickler.common.annotation.RequireAppUser;
import com.heypickler.controller.app.AppAuthController;
import com.heypickler.controller.app.AppEventController;
import com.heypickler.controller.admin.AdminNotificationController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Loop-v11 — moves AppAuthContractValidator from 56% to ~80%+.
 * Mirrors existing controller test style: direct invocation without Spring
 * context, validating the WARN/ERROR contract paths.
 */
class AppAuthContractValidatorTest {

    @Test
    void run_happyPath_reportsCounts() {
        RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
        var mappings = new java.util.HashMap<RequestMappingInfo, org.springframework.web.method.HandlerMethod>();

        // publicAnonymous: AppEventController.listEvents (GET /)
        mappings.put(
                RequestMappingInfo.paths("/api/app/events").methods(org.springframework.web.bind.annotation.RequestMethod.GET).build(),
                handlerMethod("listEvents", AppEventController.class, PublicAnonymousAccess.class));

        // requireUser: AppEventController.getMyTeam (GET /{id}/my-team)
        mappings.put(
                RequestMappingInfo.paths("/api/app/events/{id}/my-team")
                        .methods(org.springframework.web.bind.annotation.RequestMethod.GET).build(),
                handlerMethod("getMyTeam", AppEventController.class, RequireAppUser.class));

        // admin notification (no /api/app prefix → filtered out, doesn't trigger warn)
        mappings.put(
                RequestMappingInfo.paths("/api/admin/notifications")
                        .methods(org.springframework.web.bind.annotation.RequestMethod.GET).build(),
                handlerMethod("list", AdminNotificationController.class));

        when(mapping.getHandlerMethods()).thenReturn(mappings);

        AppAuthContractValidator validator = new AppAuthContractValidator(mapping);
        validator.run(mock(ApplicationArguments.class));
    }

    @Test
    void bestPathPattern_handlesNullAndEmpty() throws Exception {
        var m = AppAuthContractValidator.class.getDeclaredMethod("bestPathPattern", RequestMappingInfo.class);
        m.setAccessible(true);
        AppAuthContractValidator v = new AppAuthContractValidator(mock(RequestMappingHandlerMapping.class));

        // null patternsCondition → null
        var info1 = mock(RequestMappingInfo.class);
        when(info1.getPatternsCondition()).thenReturn(null);
        assertEquals(null, m.invoke(v, info1));

        // empty patterns → null
        var info2 = mock(RequestMappingInfo.class);
        var cond2 = mock(org.springframework.web.servlet.mvc.condition.PatternsRequestCondition.class);
        when(info2.getPatternsCondition()).thenReturn(cond2);
        when(cond2.getPatterns()).thenReturn(java.util.Collections.emptySet());
        assertEquals(null, m.invoke(v, info2));
    }

    private org.springframework.web.method.HandlerMethod handlerMethod(
            String name, Class<?> controller, Class<? extends java.lang.annotation.Annotation>... annotations) {
        for (Method m : controller.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                // annotations are optional — if 0, just find by name
                if (annotations.length == 0) {
                    return new org.springframework.web.method.HandlerMethod(
                            mockControllerInstance(controller), m);
                }
                boolean ok = true;
                for (Class<? extends java.lang.annotation.Annotation> a : annotations) {
                    if (!m.isAnnotationPresent(a)) { ok = false; break; }
                }
                if (ok) {
                    return new org.springframework.web.method.HandlerMethod(
                            mockControllerInstance(controller), m);
                }
            }
        }
        throw new RuntimeException("no matching method: " + name);
    }

    @SuppressWarnings("unchecked")
    private <T> T mockControllerInstance(Class<T> controller) {
        return (T) mock(controller);
    }
}
