package com.heypickler.service.impl;

import com.heypickler.common.constant.RedisKey;
import com.heypickler.service.DictCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DictCacheServiceImpl implements DictCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public long getVersion() {
        Object v = redisTemplate.opsForValue().get(RedisKey.dictVersion());
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }

    @Override
    public long incrementVersion() {
        Long v = redisTemplate.opsForValue().increment(RedisKey.dictVersion());
        return v == null ? 0L : v;
    }
}
