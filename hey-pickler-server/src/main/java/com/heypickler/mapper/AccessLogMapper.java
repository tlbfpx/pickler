package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.AccessLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Loop-v19 Dashboard Phase 2 V21 — access_log mapper。
 *
 * <p>由 {@code AccessLogFilter} + {@code AppTrackController} 异步写入；admin
 * 端查询 UI 留 Phase 5。
 */
@Mapper
public interface AccessLogMapper extends BaseMapper<AccessLog> {
}