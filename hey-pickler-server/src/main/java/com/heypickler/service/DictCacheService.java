package com.heypickler.service;

/**
 * 字典版本号服务（详见 RedisKey.dictVersion 的设计说明）。
 * 本期不做 per-dict / bundle 对象缓存，只维护全局版本号。
 */
public interface DictCacheService {
    /** 全局版本号；首次返回 0 */
    long getVersion();
    /** 版本号自增，返回新值 */
    long incrementVersion();
}
