package com.heypickler.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.enums.BanAction;
import com.heypickler.common.enums.UserStatus;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.util.AesUtil;
import com.heypickler.dto.admin.BanRequest;
import com.heypickler.dto.admin.UserQueryRequest;
import com.heypickler.dto.app.UserUpdateRequest;
import com.heypickler.entity.*;
import com.heypickler.mapper.*;
import com.heypickler.service.impl.UserServiceImpl;
import com.heypickler.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private EventMapper eventMapper;
    @Mock private RegistrationMapper registrationMapper;
    @Mock private PointRecordMapper pointRecordMapper;
    @Mock private BanRecordMapper banRecordMapper;
    @Mock private AesUtil aesUtil;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setNickname("测试用户");
        testUser.setAvatarUrl("http://example.com/avatar.jpg");
        testUser.setCity("北京");
        testUser.setPhone("encrypted_phone_13812341234");
        testUser.setStarPoints(100);
        testUser.setPartyPoints(50);
        testUser.setStarTier("BRONZE");
        testUser.setPartyTier("BRONZE");
        testUser.setStatus(UserStatus.NORMAL.name());
        testUser.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void getProfile_shouldReturnMaskedPhone() {
        when(userMapper.selectById(1L)).thenReturn(testUser);
        when(aesUtil.decrypt("encrypted_phone_13812341234")).thenReturn("13812341234");

        UserProfileVO profile = userService.getProfile(1L);

        assertNotNull(profile);
        assertEquals(1L, profile.getId());
        assertEquals("测试用户", profile.getNickname());
        assertEquals("138****1234", profile.getPhone());
        verify(userMapper).selectById(1L);
    }

    @Test
    void getProfile_userNotFound_shouldReturnNull() {
        when(userMapper.selectById(999L)).thenReturn(null);

        UserProfileVO profile = userService.getProfile(999L);

        assertNull(profile);
    }

    @Test
    void updateProfile_withPartialFields_shouldUpdateOnlyProvidedFields() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("新昵称");
        // city and avatarUrl are null

        userService.updateProfile(1L, request);

        verify(userMapper).updateById(argThat(user ->
                user.getId().equals(1L) &&
                        user.getNickname().equals("新昵称") &&
                        user.getCity() == null &&
                        user.getAvatarUrl() == null
        ));
    }

    @Test
    void banUser_shouldSetStatusAndCreateRecord() {
        BanRequest request = new BanRequest();
        request.setReason("违规操作");
        request.setBanUntil(LocalDateTime.now().plusDays(7));

        userService.banUser(1L, 100L, request);

        verify(userMapper).updateById(argThat(user ->
                user.getId().equals(1L) &&
                        UserStatus.BANNED.name().equals(user.getStatus())
        ));
        verify(banRecordMapper).insert(argThat(record ->
                record.getUserId().equals(1L) &&
                        record.getOperatorId().equals(100L) &&
                        BanAction.BAN.name().equals(record.getAction()) &&
                        "违规操作".equals(record.getReason())
        ));
    }

    @Test
    void unbanUser_shouldRestoreStatus() {
        userService.unbanUser(1L, 100L);

        verify(userMapper).updateById(argThat(user ->
                user.getId().equals(1L) &&
                        UserStatus.NORMAL.name().equals(user.getStatus())
        ));
        verify(banRecordMapper).insert(argThat(record ->
                record.getUserId().equals(1L) &&
                        record.getOperatorId().equals(100L) &&
                        BanAction.UNBAN.name().equals(record.getAction()) &&
                        "解除封禁".equals(record.getReason())
        ));
    }

    @Test
    void adminListUsers_withKeywordFilter_shouldReturnMatchingUsers() {
        UserQueryRequest request = new UserQueryRequest();
        request.setKeyword("测试");
        request.setPage(1);
        request.setSize(20);

        Page<User> pageResult = new Page<>(1, 20);
        pageResult.setRecords(Arrays.asList(testUser));
        pageResult.setTotal(1);

        when(userMapper.selectPage(any(Page.class), any())).thenReturn(pageResult);
        when(aesUtil.decrypt(anyString())).thenReturn("13812341234");

        PageResult<UserAdminVO> result = userService.adminListUsers(request);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals("测试用户", result.getList().get(0).getNickname());
        verify(userMapper).selectPage(any(Page.class), any());
    }

    @Test
    void adminListUsers_withStatusFilter_shouldReturnFilteredUsers() {
        UserQueryRequest request = new UserQueryRequest();
        request.setStatus(UserStatus.BANNED.name());
        request.setPage(1);
        request.setSize(20);

        Page<User> pageResult = new Page<>(1, 20);
        pageResult.setRecords(Collections.emptyList());
        pageResult.setTotal(0);

        when(userMapper.selectPage(any(Page.class), any())).thenReturn(pageResult);

        PageResult<UserAdminVO> result = userService.adminListUsers(request);

        assertNotNull(result);
        assertEquals(0, result.getList().size());
        verify(userMapper).selectPage(any(Page.class), any());
    }

    @Test
    void adminGetUser_shouldReturnDecryptedPhone() {
        when(userMapper.selectById(1L)).thenReturn(testUser);
        when(aesUtil.decrypt("encrypted_phone_13812341234")).thenReturn("13812341234");

        UserAdminVO user = userService.adminGetUser(1L);

        assertNotNull(user);
        assertEquals("13812341234", user.getPhone());
        verify(aesUtil).decrypt("encrypted_phone_13812341234");
    }

    @Test
    void getMyEvents_withRegistrations_shouldReturnEventList() {
        Registration reg = new Registration();
        reg.setId(1L);
        reg.setUserId(1L);
        reg.setEventId(10L);
        reg.setStatus(com.heypickler.common.enums.RegistrationStatus.REGISTERED.name());

        Event event = new Event();
        event.setId(10L);
        event.setTitle("测试活动");
        event.setType("MATCH");
        event.setBannerUrl("http://example.com/banner.jpg");
        event.setEventTime(LocalDateTime.now().plusDays(1));
        event.setLocation("测试地点");
        event.setStatus(com.heypickler.common.enums.EventStatus.OPEN.name());

        Page<Registration> regPage = new Page<>(1, 20);
        regPage.setRecords(Arrays.asList(reg));
        regPage.setTotal(1);

        // getMyEvents 用 selectList 拉全部 registration 后内存分页
        when(registrationMapper.selectList(any())).thenReturn(Arrays.asList(reg));
        when(eventMapper.selectBatchIds(anyList())).thenReturn(Arrays.asList(event));

        PageResult<MyEventVO> result = userService.getMyEvents(1L, null, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals("测试活动", result.getList().get(0).getTitle());
        assertEquals(com.heypickler.common.enums.RegistrationStatus.REGISTERED,
                result.getList().get(0).getRegistrationStatus());
    }

    @Test
    void getPointHistory_shouldReturnRecordsWithEventTitles() {
        PointRecord record = new PointRecord();
        record.setId(1L);
        record.setUserId(1L);
        record.setEventId(10L);
        record.setType("STAR");
        record.setPoints(10);
        record.setReason("活动奖励");
        record.setCreatedAt(LocalDateTime.now());

        Event event = new Event();
        event.setId(10L);
        event.setTitle("测试活动");

        Page<PointRecord> pointPage = new Page<>(1, 20);
        pointPage.setRecords(Arrays.asList(record));
        pointPage.setTotal(1);

        when(pointRecordMapper.selectPage(any(), any())).thenReturn(pointPage);
        when(eventMapper.selectBatchIds(anyList())).thenReturn(Arrays.asList(event));

        PageResult<PointRecordVO> result = userService.getPointHistory(1L, "STAR", 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals("测试活动", result.getList().get(0).getEventTitle());
        assertEquals(10, result.getList().get(0).getPoints());
    }
}
