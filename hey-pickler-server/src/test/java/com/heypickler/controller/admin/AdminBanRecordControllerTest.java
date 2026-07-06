package com.heypickler.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.util.AesUtil;
import com.heypickler.entity.AdminUser;
import com.heypickler.entity.BanRecord;
import com.heypickler.entity.User;
import com.heypickler.mapper.AdminUserMapper;
import com.heypickler.mapper.BanRecordMapper;
import com.heypickler.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Loop-v10 — moves AdminBanRecordController from 0% to ~80%+.
 * Cover the list filter + the operator/user enrichment branches plus delete
 * not-found.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBanRecordControllerTest {

    @Mock private BanRecordMapper banRecordMapper;
    @Mock private UserMapper userMapper;
    @Mock private AdminUserMapper adminUserMapper;
    @Mock private AesUtil aesUtil;
    @InjectMocks private AdminBanRecordController controller;

    @BeforeAll
    static void warmLambdaCache() {
        Configuration cfg = new Configuration();
        for (Class<?> c : List.of(BanRecord.class, User.class, AdminUser.class)) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
            a.setCurrentNamespace("com.heypickler.mapper." + c.getSimpleName() + "Mapper");
            TableInfoHelper.initTableInfo(a, c);
        }
    }

    private BanRecord sampleRecord() {
        BanRecord r = new BanRecord();
        r.setId(1L);
        r.setUserId(11L);
        r.setOperatorId(99L);
        r.setAction("BAN");
        r.setReason("违规");
        r.setBanUntil(LocalDateTime.now().plusDays(7));
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    @Test
    void list_emptyResults_returnsEmptyPage() {
        Page<BanRecord> page = new Page<>(1, 20);
        page.setTotal(0L);
        doReturn(page).when(banRecordMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        assertEquals(0L, controller.list(1, 20, null, null).getData().getTotal());
    }

    @Test
    void list_enrichesUserAndOperator() {
        BanRecord r = sampleRecord();
        Page<BanRecord> page = new Page<>(1, 20);
        page.setTotal(1L);
        page.setRecords(List.of(r));
        doReturn(page).when(banRecordMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        User u = new User();
        u.setId(11L);
        u.setNickname("alice");
        u.setPhone("encrypted");
        AdminUser op = new AdminUser();
        op.setId(99L);
        op.setUsername("admin1");
        doReturn(List.of(u)).when(userMapper).selectBatchIds(anyList());
        doReturn(List.of(op)).when(adminUserMapper).selectBatchIds(anyList());
        doReturn("13800001111").when(aesUtil).decrypt("encrypted");

        var data = controller.list(1, 20, null, null).getData();
        assertEquals(1L, data.getTotal());
        var vo = data.getList().get(0);
        assertEquals("alice", vo.getUserNickname());
        assertEquals("13800001111", vo.getUserPhone());
        assertEquals("admin1", vo.getOperatorName());
    }

    @Test
    void list_aesDecryptFailure_fallsBackToRawValue() {
        BanRecord r = sampleRecord();
        Page<BanRecord> page = new Page<>(1, 20);
        page.setTotal(1L);
        page.setRecords(List.of(r));
        doReturn(page).when(banRecordMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        User u = new User();
        u.setId(11L);
        u.setNickname("bob");
        u.setPhone("garbled");
        doReturn(List.of(u)).when(userMapper).selectBatchIds(anyList());
        doReturn(List.<AdminUser>of()).when(adminUserMapper).selectBatchIds(anyList());
        doThrow(new RuntimeException("bad key")).when(aesUtil).decrypt("garbled");

        var data = controller.list(1, 20, null, null).getData();
        var vo = data.getList().get(0);
        assertEquals("garbled", vo.getUserPhone());  // falls back to raw on failure
        assertEquals("bob", vo.getUserNickname());
        assertNull(vo.getOperatorName());
    }

    @Test
    void list_userNotFound_skipsEnrichment() {
        BanRecord r = sampleRecord();
        Page<BanRecord> page = new Page<>(1, 20);
        page.setTotal(1L);
        page.setRecords(List.of(r));
        doReturn(page).when(banRecordMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        doReturn(List.<User>of()).when(userMapper).selectBatchIds(anyList());
        doReturn(List.<AdminUser>of()).when(adminUserMapper).selectBatchIds(anyList());

        var data = controller.list(1, 20, null, null).getData();
        var vo = data.getList().get(0);
        assertNull(vo.getUserNickname());
        assertNull(vo.getUserPhone());
    }

    @Test
    void delete_existingRecord_succeeds() {
        BanRecord r = sampleRecord();
        doReturn(r).when(banRecordMapper).selectById(1L);
        controller.delete(1L);
    }

    @Test
    void delete_missingRecord_throwsNotFound() {
        doReturn(null).when(banRecordMapper).selectById(99L);
        assertThrows(BizException.class, () -> controller.delete(99L));
    }
}
