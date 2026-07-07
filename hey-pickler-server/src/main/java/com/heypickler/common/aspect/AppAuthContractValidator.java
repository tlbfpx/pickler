package com.heypickler.common.aspect;

import com.heypickler.common.annotation.PublicAnonymousAccess;
import com.heypickler.common.annotation.RequireAppUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

/**
 * Loop-v2 D9 — startup validator for /api/app/* handler auth annotations.
 *
 * <p>Walk every registered {@code RequestMapping} at boot. For any handler
 * whose path starts with {@code /api/app/}, log the auth posture and emit
 * WARN when both annotations are absent (a future developer's new endpoint
 * will be flagged automatically) or both are present (a contract violation).
 *
 * <p>The filter keeps its {@code PUBLIC_GET_PREFIXES} bypass for now, but
 * this validator adds a regression net: any new app endpoint that lands in a
 * PR without an annotation will surface in the boot log.
 */
@Slf4j
@Component
public class AppAuthContractValidator implements ApplicationRunner {

    private final RequestMappingHandlerMapping mapping;

    public AppAuthContractValidator(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping) {
        this.mapping = mapping;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<RequestMappingInfo, HandlerMethod> handlers = mapping.getHandlerMethods();
        int total = 0;
        int annotated = 0;
        int missing = 0;
        int conflicting = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlers.entrySet()) {
            RequestMappingInfo info = entry.getKey();
            String pathPattern = bestPathPattern(info);
            if (pathPattern == null || !pathPattern.startsWith("/api/app/")) {
                continue;
            }
            total++;

            HandlerMethod method = entry.getValue();
            boolean publicAnon = method.hasMethodAnnotation(PublicAnonymousAccess.class);
            boolean requireUser = method.hasMethodAnnotation(RequireAppUser.class);

            if (publicAnon && requireUser) {
                conflicting++;
                log.error("[D9] {} {} has BOTH @PublicAnonymousAccess and @RequireAppUser — "
                        + "annotations are mutually exclusive.", pathPattern, info.getMethodsCondition());
                continue;
            }
            if (publicAnon || requireUser) {
                annotated++;
                continue;
            }
            missing++;
            log.warn("[D9] {} {} has NO @PublicAnonymousAccess or @RequireAppUser annotation — "
                    + "AppAuthFilter will fall back to its hardcoded prefix list, which is fragile. "
                    + "Add one of the two annotations before adding new app endpoints.",
                    info.getMethodsCondition(), pathPattern);
        }
        log.info("[D9] AppAuthContractValidator: scanned {} handler method(s) under /api/app/* "
                + "— annotated={}, missing={}, conflicting={}", total, annotated, missing, conflicting);
    }

    private static String bestPathPattern(RequestMappingInfo info) {
        var condition = info.getPatternsCondition();
        if (condition == null) return null;
        var patterns = condition.getPatterns();
        if (patterns == null || patterns.isEmpty()) return null;
        return patterns.iterator().next();
    }
}
