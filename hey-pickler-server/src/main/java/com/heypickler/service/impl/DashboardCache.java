package com.heypickler.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Dashboard 缓存层（Loop-v19 Phase 1 commit C）。
 *
 * <p>5 分钟 TTL，key 前缀 {@code heypickler:dashboard:*}。序列化为 JSON 字符串（不走
 * RedisTemplate 的 Jackson2JsonRedisSerializer，避免 default typing 关闭时的 LinkedHashMap 陷阱）。
 *
 * <p>失败降级：Redis 异常 → 不抛给上层，service 继续查 DB（cache 是 best-effort 加速器）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardCache {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /** 读 JSON 字符串 → 反序列化成 {@code type}；无 cache / 解析失败 → 返回 null。 */
    public <T> T get(String key, Class<T> type) {
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw == null) return null;
            if (type.isInstance(raw)) return type.cast(raw);
            // raw 可能是 String（其他路径写入）或 LinkedHashMap（Jackson generic）；统一反序列化一次
            String json = raw instanceof String s ? s : objectMapper.writeValueAsString(raw);
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("DashboardCache.get({}) failed, fallback to DB: {}", key, e.toString());
            return null;
        }
    }

    /** 读取带 TypeReference 的复杂类型（如 {@code Map<String,Object>}、{@code List<VO>}）。 */
    public <T> T get(String key, TypeReference<T> typeRef) {
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw == null) return null;
            String json = raw instanceof String s ? s : objectMapper.writeValueAsString(raw);
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("DashboardCache.get({}) typeRef failed: {}", key, e.toString());
            return null;
        }
    }

    /** 写：序列化为 JSON 字符串，TTL 5 分钟。失败仅 warn（cache 不可用是软降级）。 */
    public void put(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, TTL);
        } catch (Exception e) {
            log.warn("DashboardCache.put({}) failed, ignored: {}", key, e.toString());
        }
    }
}
