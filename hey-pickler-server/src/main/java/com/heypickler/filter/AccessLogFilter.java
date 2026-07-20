package com.heypickler.filter;

import com.heypickler.common.util.IpResolver;
import com.heypickler.entity.AccessLog;
import com.heypickler.service.AccessLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Loop-v19 Dashboard Phase 2 R2 — 全量 API 访问日志 filter。
 *
 * <p>挂在 {@link Ordered#LOWEST_PRECEDENCE} 之前（{@code LOWEST_PRECEDENCE - 10}），
 * 保证在 SecurityHeadersFilter 等最末尾 filter 之前拿到延迟统计；比 auth
 * filters 晚没关系——{@code userId}/{@code adminId} 通过
 * {@code request.getAttribute()} 在响应时点已绑定。
 *
 * <p>包全部 {@code /api/**} 请求，**包括**鉴权失败（401/403）。任何 request
 * attribute 缺失视为匿名，{@code userId = adminId = null}。写入失败 catch
 * all + warn，不抛——access log 是 best-effort，不能污染原请求响应。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class AccessLogFilter extends OncePerRequestFilter {

    private final AccessLogService accessLogService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 仅记录 /api/**；/actuator / swagger / static 等不入日志
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long t0 = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            // 无论 chain 抛异常还是正常返回，try-finally 都记录；任何异常都被
            // 这里吞掉（与 OperationLogAspect 的 fire-and-forget 模式一致）。
            try {
                int latency = (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - t0);
                AccessLog entry = new AccessLog();
                entry.setPath(request.getRequestURI());
                entry.setMethod(request.getMethod());
                entry.setStatusCode(response.getStatus());
                entry.setLatencyMs(latency);
                Object userId = request.getAttribute("userId");
                Object adminId = request.getAttribute("adminId");
                if (userId instanceof Number n) entry.setUserId(n.longValue());
                if (adminId instanceof Number n) entry.setAdminId(n.longValue());
                entry.setIp(IpResolver.resolveIp(request));
                String ua = request.getHeader("User-Agent");
                if (ua != null && ua.length() > 256) ua = ua.substring(0, 256);
                entry.setUserAgent(ua);
                accessLogService.recordAccess(entry);
            } catch (Exception ex) {
                // access log 写入失败绝不影响原请求响应
                log.warn("AccessLogFilter recordAccess failed for {} {}: {}",
                        request.getMethod(), request.getRequestURI(), ex.toString());
            }
        }
    }
}