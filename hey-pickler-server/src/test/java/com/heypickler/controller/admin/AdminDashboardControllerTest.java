package com.heypickler.controller.admin;

import com.heypickler.service.DashboardService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Loop-v19 Dashboard Phase 1：聚合逻辑下沉到 DashboardService 后，
 * Controller 只做路由 + RBAC 透传。本测试只校验 controller 调 service 一次并包装 Result.ok。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private AdminDashboardController controller;

    @BeforeEach
    void setUp() {
        // 默认 stub：空快照
        when(dashboardService.getSnapshot(anyBoolean())).thenReturn(new LinkedHashMap<>());
    }

    @Test
    void getStats_invokesDashboardServiceAndWrapsInResultOk() {
        // 构造一个含 recentRegistrations 的 stub snapshot，确认 controller 调 DashboardService 并透传
        Map<String, Object> snapshot = new LinkedHashMap<>();
        List<Map<String, Object>> recent = Arrays.asList(
                Map.of("id", 1L, "nickname", "李明辉", "eventTitle", "周末赛"));
        snapshot.put("recentRegistrations", recent);
        snapshot.put("totalUsers", 123L);
        when(dashboardService.getSnapshot(anyBoolean())).thenReturn(snapshot);

        Map<String, Object> result = controller.getStats(null).getData();

        verify(dashboardService, times(1)).getSnapshot(anyBoolean());
        assertEquals(123L, result.get("totalUsers"));
        assertEquals(1, ((List<?>) result.get("recentRegistrations")).size());
        assertEquals("李明辉",
                ((List<Map<String, Object>>) result.get("recentRegistrations")).get(0).get("nickname"));
    }

    @Test
    void getStats_returnsEmptySnapshot() {
        Map<String, Object> result = controller.getStats(null).getData();
        assertNotNull(result);
        assertTrue(result.isEmpty(), "应返回 service 给的空快照");
    }
}
