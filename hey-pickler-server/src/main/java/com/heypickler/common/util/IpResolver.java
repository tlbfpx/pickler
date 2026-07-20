package com.heypickler.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析工具（Loop-v19 Dashboard Phase 2 R1/R2 抽出）。
 *
 * <p>从 {@code OperationLogAspect.resolveIp} 抽出，供 {@code AccessLogFilter} +
 * {@code OperationLogAspect} + 未来其他 filter 复用。优先级：
 * <ol>
 *   <li>{@code X-Forwarded-For} 第一跳（多级反代场景）</li>
 *   <li>{@code request.getRemoteAddr()} fallback</li>
 * </ol>
 */
public final class IpResolver {

    private IpResolver() {}

    /**
     * 解析客户端真实 IP。{@code X-Forwarded-For} 缺失或空时返回 {@code null}
     * （与原 {@code OperationLogAspect.resolveIp} 行为兼容：原方法也仅 fallback 到
     * {@code getRemoteAddr()}；本 util 增加 null 透传便于单元测试）。
     */
    public static String resolveIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            int comma = xff.indexOf(',');
            String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
            return first.isEmpty() ? null : first;
        }
        String remote = request.getRemoteAddr();
        return remote;
    }
}