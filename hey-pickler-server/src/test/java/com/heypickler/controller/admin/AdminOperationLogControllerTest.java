package com.heypickler.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.dto.OperationLogQuery;
import com.heypickler.entity.AdminUser;
import com.heypickler.entity.OperationLog;
import com.heypickler.mapper.AdminUserMapper;
import com.heypickler.service.OperationLogService;
import com.heypickler.vo.OperationLogVO;
import com.heypickler.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminOperationLogControllerTest {

    @Mock private OperationLogService operationLogService;
    @Mock private AdminUserMapper adminUserMapper;
    @InjectMocks private AdminOperationLogController controller;

    private OperationLog sampleRow() {
        OperationLog log = new OperationLog();
        log.setId(1L);
        log.setOperatorId(7L);
        log.setOperatorRole("SUPER_ADMIN");
        log.setMethod("POST");
        log.setModule("USER");
        log.setAction("BAN");
        log.setTargetType("User");
        log.setTargetId("123");
        log.setPath("/api/admin/users/123/ban");
        log.setParams("{\"reason\":\"违规\"}");
        log.setStatus(1);
        log.setLatencyMs(15);
        log.setIp("10.0.0.1");
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    @BeforeEach
    void stubAdminUser() {
        AdminUser admin = new AdminUser();
        admin.setId(7L);
        admin.setUsername("alice");
        when(adminUserMapper.selectBatchIds(anyList())).thenReturn(List.of(admin));
    }

    @Test
    void list_returnsPageAndEnrichesOperatorName() {
        Page<OperationLog> page = new Page<>(1, 20);
        page.setTotal(1L);
        page.setRecords(List.of(sampleRow()));
        when(operationLogService.page(any(OperationLogQuery.class), eq(1), eq(20)))
            .thenReturn(page);

        PageResult<OperationLogVO> result = controller.list(
            1, 20, null, "USER", null, null, null, null).getData();

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        OperationLogVO vo = result.getList().get(0);
        assertEquals("alice", vo.getOperatorName(), "operator name should be enriched");
        assertEquals("USER", vo.getModule());
        assertEquals("BAN", vo.getAction());
        assertEquals("123", vo.getTargetId());
        assertEquals(1, vo.getStatus());
    }

    @Test
    void list_emptyResult_skipsBatchLoad() {
        Page<OperationLog> empty = new Page<>(1, 20);
        empty.setTotal(0L);
        empty.setRecords(List.of());
        when(operationLogService.page(any(), eq(1), eq(20))).thenReturn(empty);

        PageResult<OperationLogVO> result = controller.list(
            1, 20, null, null, null, null, null, null).getData();

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(adminUserMapper, never()).selectBatchIds(anyList());
    }

    @Test
    void list_nullOperator_keepsNullName() {
        OperationLog log = sampleRow();
        log.setOperatorId(null);
        log.setOperatorRole("ANONYMOUS");
        Page<OperationLog> page = new Page<>(1, 20);
        page.setTotal(1L);
        page.setRecords(List.of(log));
        when(operationLogService.page(any(), eq(1), eq(20))).thenReturn(page);

        PageResult<OperationLogVO> result = controller.list(
            1, 20, null, null, null, null, null, null).getData();

        OperationLogVO vo = result.getList().get(0);
        assertNull(vo.getOperatorId());
        assertEquals("ANONYMOUS", vo.getOperatorRole());
        assertNull(vo.getOperatorName());
    }
}
