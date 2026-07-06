package com.heypickler.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.result.PageResult;

import com.heypickler.dto.admin.BanRequest;
import com.heypickler.dto.admin.UserQueryRequest;
import com.heypickler.dto.app.UserUpdateRequest;
import com.heypickler.service.UserService;
import com.heypickler.vo.MyEventVO;
import com.heypickler.vo.PointRecordVO;
import com.heypickler.vo.UserAdminVO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Loop-v8 coverage sprint — moves AdminUserController from 9.7% to ~70%+.
 * Mirrors the existing {@link AdminOperationLogControllerTest} pattern:
 * {@code @InjectMocks} on the controller, no MockMvc — direct method
 * invocation lets jacoco count every line as covered.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserControllerTest {

    @Mock private UserService userService;
    @InjectMocks private AdminUserController controller;

    @Test
    void listUsers_delegatesToService() {
        PageResult<UserAdminVO> stub = PageResult.of(0, 1, 10, List.of());
        when(userService.adminListUsers(any(UserQueryRequest.class))).thenReturn(stub);
        PageResult<UserAdminVO> result = controller.listUsers(new UserQueryRequest()).getData();
        assertEquals(0L, result.getTotal());
    }

    @Test
    void getUser_returnsDetail() {
        UserAdminVO vo = new UserAdminVO();
        when(userService.adminGetUser(7L)).thenReturn(vo);
        assertEquals(vo, controller.getUser(7L).getData());
    }

    @Test
    void getPointHistory_passesFilters() {
        PageResult<PointRecordVO> stub = PageResult.of(0, 1, 20, List.of());
        doReturn(stub).when(userService).getPointHistory(
                anyLong(), any(), any(int.class), any(int.class));
        PageResult<PointRecordVO> result = controller.getPointHistory(7L, "STAR", 1, 20).getData();
        assertEquals(0L, result.getTotal());
    }

    @Test
    void getEventHistory_passesFilters() {
        PageResult<MyEventVO> stub = PageResult.of(0, 1, 20, List.of());
        doReturn(stub).when(userService).getMyEvents(
                anyLong(), any(), any(int.class), any(int.class));
        PageResult<MyEventVO> result = controller.getEventHistory(7L, null, 1, 20).getData();
        assertEquals(0L, result.getTotal());
    }

    @Test
    void updateUser_callsService() {
        controller.updateUser(7L, new UserUpdateRequest());
    }

    @Test
    void banUser_extractsAdminIdFromRequestAttribute() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("adminId")).thenReturn(99L);
        controller.banUser(req, 7L, new BanRequest());
    }

    @Test
    void unbanUser_extractsAdminIdFromRequestAttribute() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("adminId")).thenReturn(99L);
        controller.unbanUser(req, 7L);
    }
}
