package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Loop-v19 Dashboard Phase 2 V21 — login_log mapper。
 *
 * <p>基类 {@code BaseMapper} 提供 insert + selectById + deleteById 等基础能力；
 * 复杂查询（同期群 / 漏斗）由 Service 层在 Phase 3 用 {@code @Select} 注解补。
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}