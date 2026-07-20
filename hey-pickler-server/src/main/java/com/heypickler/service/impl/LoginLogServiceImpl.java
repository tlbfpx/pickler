package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heypickler.entity.LoginLog;
import com.heypickler.mapper.LoginLogMapper;
import com.heypickler.service.LoginLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Loop-v19 Dashboard Phase 2 — LoginLogService 实现。
 *
 * <p>所有 insert 走 {@link Async}，调用方不阻塞（典型调用：登录 controller
 * 在 success / fail 分支同步 recordLogin，几 ms 返回用户）。
 */
@Slf4j
@Service
public class LoginLogServiceImpl extends ServiceImpl<LoginLogMapper, LoginLog> implements LoginLogService {

    @Override
    @Async("loginLogExecutor")
    public void recordLogin(LoginLog loginLog) {
        if (loginLog == null) return;
        try {
            save(loginLog);
        } catch (Exception e) {
            // append-only 数据源写失败：warn 但不抛（不污染登录响应）
            log.warn("recordLogin failed: userId={} channel={} result={}: {}",
                    loginLog.getUserId(), loginLog.getChannel(), loginLog.getLoginResult(), e.toString());
        }
    }

    @Override
    public long countByResultInRange(String result, LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<LoginLog> q = new LambdaQueryWrapper<>();
        if (result != null && !result.isBlank()) {
            q.eq(LoginLog::getLoginResult, result);
        }
        q.ge(LoginLog::getCreatedAt, from).lt(LoginLog::getCreatedAt, to);
        return count(q);
    }
}