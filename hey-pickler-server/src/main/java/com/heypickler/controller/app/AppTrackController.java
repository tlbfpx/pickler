package com.heypickler.controller.app;

import com.heypickler.common.dto.TrackEventRequest;
import com.heypickler.common.result.Result;
import com.heypickler.entity.AccessLog;
import com.heypickler.service.AccessLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Loop-v19 Dashboard Phase 2 R3 — 小程序客户端事件上报通道。
 *
 * <p>{@code POST /api/app/track/event}：
 * <ul>
 *   <li>无独立鉴权：AppAuthFilter 自动绑 userId（若有 JWT），匿名请求 userId=null</li>
 *   <li>写一条 access_log（path=/api/app/track/event, status=200, error_msg=name）</li>
 *   <li>异步写，50ms 内返回（不阻塞小程序主线程）</li>
 *   <li>props size 校验：JSON 序列化后 > 2KB → 400</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/app/track")
@RequiredArgsConstructor
public class AppTrackController {

    /** props JSON 序列化上限（2 KB）。 */
    private static final int PROPS_MAX_BYTES = 2 * 1024;

    private final AccessLogService accessLogService;

    @PostMapping("/event")
    public Result<Void> track(@Valid @RequestBody TrackEventRequest req,
                              HttpServletRequest httpRequest) {
        // props 体积校验：序列化后超 2 KB 直接 400
        if (req.getProps() != null && !req.getProps().isEmpty()) {
            int size = estimateSize(req.getProps());
            if (size > PROPS_MAX_BYTES) {
                return Result.fail(400, "props 体积超过 2KB 上限（实际 " + size + " bytes）");
            }
        }

        // 写 access_log（异步）：用 error_msg 字段塞事件名便于 SQL 检索
        AccessLog entry = new AccessLog();
        entry.setPath("/api/app/track/event");
        entry.setMethod("POST");
        entry.setStatusCode(200);
        entry.setLatencyMs(0);
        Object userId = httpRequest.getAttribute("userId");
        if (userId instanceof Number n) entry.setUserId(n.longValue());
        entry.setErrorMsg(req.getName());
        // 设备 id 透传进 userAgent 列位（access_log 无 did 列，复用 UA 字段加前缀）
        if (req.getDid() != null && !req.getDid().isBlank()) {
            String ua = "did=" + req.getDid();
            entry.setUserAgent(ua.length() > 256 ? ua.substring(0, 256) : ua);
        }
        accessLogService.recordAccess(entry);

        return Result.ok();
    }

    /**
     * props 体积粗略估算：每个 key+value 字符串长度求和 + 结构开销。
     * 简化版：toString 后 byte 长度。避免引入 Jackson ObjectMapper 依赖。
     */
    private static int estimateSize(Map<String, Object> props) {
        int total = 16; // JSON 包装 + 引号 + 分隔符基础开销
        for (Map.Entry<String, Object> e : props.entrySet()) {
            total += e.getKey().length() + 4;
            Object v = e.getValue();
            if (v != null) total += String.valueOf(v).length();
        }
        return total;
    }
}