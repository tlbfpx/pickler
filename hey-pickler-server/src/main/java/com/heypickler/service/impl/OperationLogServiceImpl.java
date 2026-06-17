package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.dto.OperationLogQuery;
import com.heypickler.entity.OperationLog;
import com.heypickler.mapper.OperationLogMapper;
import com.heypickler.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Override
    @Async("auditLogExecutor")
    public void record(OperationLog entry) {
        try {
            operationLogMapper.insert(entry);
        } catch (Exception e) {
            // Audit failure must never propagate. The aspect also wraps this call,
            // but defending here too in case the @Async proxy boundary swallows the
            // outer try/catch in some Spring versions.
            log.error("Failed to persist operation_log: {}", entry, e);
        }
    }

    @Override
    public IPage<OperationLog> page(OperationLogQuery q, int page, int size) {
        LambdaQueryWrapper<OperationLog> w = new LambdaQueryWrapper<>();
        if (q != null) {
            if (q.getOperatorId() != null) w.eq(OperationLog::getOperatorId, q.getOperatorId());
            if (q.getModule() != null && !q.getModule().isEmpty()) w.eq(OperationLog::getModule, q.getModule());
            if (q.getAction() != null && !q.getAction().isEmpty()) w.eq(OperationLog::getAction, q.getAction());
            if (q.getStatus() != null) w.eq(OperationLog::getStatus, q.getStatus());
            if (q.getStartTime() != null) w.ge(OperationLog::getCreatedAt, q.getStartTime());
            if (q.getEndTime() != null) w.le(OperationLog::getCreatedAt, q.getEndTime());
        }
        w.orderByDesc(OperationLog::getCreatedAt);
        return operationLogMapper.selectPage(new Page<>(page, size), w);
    }
}
