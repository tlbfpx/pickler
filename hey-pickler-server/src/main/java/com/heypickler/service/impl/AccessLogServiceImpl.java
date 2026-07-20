package com.heypickler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heypickler.entity.AccessLog;
import com.heypickler.mapper.AccessLogMapper;
import com.heypickler.service.AccessLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Loop-v19 Dashboard Phase 2 — AccessLogService 实现。
 *
 * <p>复用 {@code loginLogExecutor}：access log 量级 ≤ login log，2/4/500
 * 队列足够。Phase 3 量起来后再独立 executor（参考 {@code AsyncConfig.loginLogExecutor}）。
 */
@Slf4j
@Service
public class AccessLogServiceImpl extends ServiceImpl<AccessLogMapper, AccessLog> implements AccessLogService {

    @Override
    @Async("loginLogExecutor")
    public void recordAccess(AccessLog accessLog) {
        if (accessLog == null) return;
        try {
            save(accessLog);
        } catch (Exception e) {
            // 写失败不影响原请求响应（filter 调用方已在外层 catch）
            log.warn("recordAccess failed: path={} method={} status={}: {}",
                    accessLog.getPath(), accessLog.getMethod(), accessLog.getStatusCode(), e.toString());
        }
    }
}