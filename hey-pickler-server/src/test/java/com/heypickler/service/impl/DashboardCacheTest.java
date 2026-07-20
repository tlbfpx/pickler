package com.heypickler.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Loop-v19 Dashboard Phase 1 commit C：DashboardCache 单测。
 *
 * <p>覆盖三类读路径（hit String / hit LinkedHashMap / miss）+ 写路径 + 异常降级。Redis
 * 异常不应抛给上层（cache 是 best-effort 加速器，service 仍能继续查 DB）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardCacheTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;

    ObjectMapper objectMapper;
    DashboardCache cache;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cache = new DashboardCache(redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ============ get(key, Class) ============

    @Test
    void get_classHit_returnsParsedObject() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "alice");
        raw.put("age", 30);
        // 模拟 Redis 存的是 LinkedHashMap（Jackson generic 反序列化默认形态）
        when(valueOps.get("k1")).thenReturn(raw);
        Map result = cache.get("k1", Map.class);
        assertNotNull(result);
        assertEquals("alice", result.get("name"));
    }

    @Test
    void get_classHitString_returnsParsedObject() {
        // 模拟 Redis 存的是 JSON 字符串（更典型的 default typing 关闭路径）
        String json = "{\"name\":\"bob\",\"age\":42}";
        when(valueOps.get("k2")).thenReturn(json);
        Map result = cache.get("k2", Map.class);
        assertNotNull(result);
        assertEquals("bob", result.get("name"));
    }

    @Test
    void get_classMiss_returnsNull() {
        when(valueOps.get("miss")).thenReturn(null);
        assertNull(cache.get("miss", Map.class));
    }

    @Test
    void get_classRedisException_returnsNull() {
        when(valueOps.get("boom")).thenThrow(new RuntimeException("redis down"));
        // 不应抛给上层
        assertNull(cache.get("boom", Map.class));
    }

    // ============ get(key, TypeReference) ============

    @Test
    void get_typeRefHit_returnsList() throws Exception {
        // List<Map> 用 TypeReference<List<Map<String,Object>>> 反序列化
        List<Map<String, Object>> list = Arrays.asList(
                Map.of("id", 1, "title", "Event A"),
                Map.of("id", 2, "title", "Event B")
        );
        String json = objectMapper.writeValueAsString(list);
        when(valueOps.get("top")).thenReturn(json);
        TypeReference<List<Map<String, Object>>> typeRef = new TypeReference<>() {};
        List<Map<String, Object>> result = cache.get("top", typeRef);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Event A", result.get(0).get("title"));
    }

    @Test
    void get_typeRefMiss_returnsNull() {
        when(valueOps.get("none")).thenReturn(null);
        assertNull(cache.get("none", new TypeReference<List<Map<String, Object>>>() {}));
    }

    @Test
    void get_typeRefRedisException_returnsNull() {
        when(valueOps.get("bad")).thenThrow(new RuntimeException("conn refused"));
        assertNull(cache.get("bad", new TypeReference<List<Map<String, Object>>>() {}));
    }

    // ============ put ============

    @Test
    void put_serializesAsJson_setsFiveMinTtl() {
        Map<String, Object> value = Map.of("totalUsers", 123L, "newUsersWeek", 7L);
        cache.put("heypickler:dashboard:snapshot", value);
        verify(valueOps, times(1)).set(eq("heypickler:dashboard:snapshot"), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void put_serializationFailure_swallows() {
        // 用一个会失败的 ObjectMapper 模拟序列化异常
        ObjectMapper broken = new ObjectMapper();
        // 利用一个无法序列化的对象（这里用 mock 对象就行 —— ObjectMapper 不抛则测试无意义，
        // 简单起见：传 null 触发 NPE 也算异常降级）
        // null 是合法序列化（输出 "null"），不会抛。这里直接传一个会被视为 raw value 的对象也行。
        // 走另一条路径：mock redisTemplate 抛异常
        org.mockito.Mockito.doThrow(new RuntimeException("redis write fail"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));
        // 不抛给上层
        cache.put("k", Map.of("x", 1));
        // 期望至少调用了一次 put（最终被吞掉）
        verify(valueOps, times(1)).set(eq("k"), anyString(), any(Duration.class));
    }

    @Test
    void put_serializeException_swallows() {
        // 用 anonymous class 强制 ObjectMapper.writeValueAsString 抛异常
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.core.JsonProcessingException("cannot serialize") {};
            }
        };
        DashboardCache localCache = new DashboardCache(redisTemplate, failingMapper);
        // 不抛
        localCache.put("any-key", Map.of("a", 1));
        // writeValueAsString 抛了，所以 redisTemplate.opsForValue().set() 不应该被调用
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // ============ 兜底 ============

    @Test
    void get_class_instanceCheckShortCircuit_returnsRawDirectly() {
        // type.isInstance(raw) 直接 cast 的快路径：传入的 raw 已经是目标类型
        // 这里用 DashboardCacheTest.class 自身作为 raw，target type 同类
        DashboardCacheTest sentinel = new DashboardCacheTest();
        when(valueOps.get("self")).thenReturn(sentinel);
        Object result = cache.get("self", DashboardCacheTest.class);
        assertTrue(result == sentinel, "should short-circuit via isInstance check");
    }
}