package com.heypickler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.heypickler.common.dto.OperationLogQuery;
import com.heypickler.entity.OperationLog;

public interface OperationLogService {
    /**
     * Persist an audit record asynchronously. Never throws — caller (the aspect)
     * relies on fire-and-forget semantics so audit failures cannot break the
     * admin request being logged.
     */
    void record(OperationLog log);

    IPage<OperationLog> page(OperationLogQuery query, int page, int size);
}
