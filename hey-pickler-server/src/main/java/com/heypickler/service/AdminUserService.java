package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.AdminUserCreateRequest;
import com.heypickler.vo.AdminUserVO;

public interface AdminUserService {
    PageResult<AdminUserVO> listAdminUsers(int page, int size);
    AdminUserVO getAdminUser(Long id);
    Long createAdminUser(AdminUserCreateRequest request);
    void updateAdminUser(Long id, String role, String status);
    void resetPassword(Long id, String newPassword);
}
