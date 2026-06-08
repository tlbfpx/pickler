package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.BanRequest;
import com.heypickler.dto.admin.UserQueryRequest;
import com.heypickler.dto.app.UserUpdateRequest;
import com.heypickler.vo.*;

public interface UserService {
    UserProfileVO getProfile(Long userId);

    void updateProfile(Long userId, UserUpdateRequest request);

    PageResult<MyEventVO> getMyEvents(Long userId, String type, int page, int size);

    PageResult<PointRecordVO> getPointHistory(Long userId, String type, int page, int size);

    PageResult<UserAdminVO> adminListUsers(UserQueryRequest request);

    UserAdminVO adminGetUser(Long userId);

    void adminUpdateUser(Long userId, UserUpdateRequest request);

    void banUser(Long userId, Long operatorId, BanRequest request);

    void unbanUser(Long userId, Long operatorId);
}
