package com.heypickler.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.service.TierResolver;
import com.heypickler.entity.Event;
import com.heypickler.entity.Registration;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDashboardControllerTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private EventMapper eventMapper;
    @Mock
    private RegistrationMapper registrationMapper;
    @Mock
    private TierResolver tierResolver;

    @InjectMocks
    private AdminDashboardController controller;

    private User existingUser;

    @BeforeEach
    void setUp() {
        // 全局打默认 stub，避免 selectCount 链路 NPE
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(eventMapper.selectCount(any())).thenReturn(0L);
        when(registrationMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(eventMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(registrationMapper.selectList(any())).thenReturn(Collections.emptyList());
        // TierResolver.defaultKey("STAR") 兜底档 = BRONZE（tier_config V19 seed）
        when(tierResolver.defaultKey(anyString())).thenReturn("BRONZE");

        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setNickname("李明辉");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getStats_RecentRegistrations_ShouldOmitOrphanRows() {
        // 两条 registration，一条 user_id=1（存在），一条 user_id=999（孤儿）
        Registration reg1 = new Registration();
        reg1.setId(1L);
        reg1.setUserId(1L);
        reg1.setEventId(10L);
        reg1.setStatus("REGISTERED");

        Registration orphan = new Registration();
        orphan.setId(2L);
        orphan.setUserId(999L);
        orphan.setEventId(10L);
        orphan.setStatus("REGISTERED");

        // 仅对 recent registrations 查询返回这两条
        when(registrationMapper.selectList(any())).thenReturn(Arrays.asList(reg1, orphan));
        when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(existingUser));
        when(eventMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        Map<String, Object> result = controller.getStats().getData();

        List<Map<String, Object>> recent = (List<Map<String, Object>>) result.get("recentRegistrations");
        assertEquals(1, recent.size(), "orphan registration should be excluded");
        assertEquals("李明辉", recent.get(0).get("nickname"));
        assertNotEquals("未知", recent.get(0).get("nickname"),
            "should not fall back to placeholder '未知'");
    }
}
