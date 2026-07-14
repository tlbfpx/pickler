package com.heypickler.service;

import com.heypickler.service.impl.DictCacheServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DictCacheServiceImplTest {

    @InjectMocks DictCacheServiceImpl service;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;

    @Test
    void getVersion_absentReturnsZero() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("heypickler:dict:version")).thenReturn(null);
        assertEquals(0L, service.getVersion());
    }

    @Test
    void getVersion_numericValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("heypickler:dict:version")).thenReturn(7);
        assertEquals(7L, service.getVersion());
    }

    @Test
    void incrementVersion_returnsNewValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("heypickler:dict:version")).thenReturn(8L);
        assertEquals(8L, service.incrementVersion());
    }
}
