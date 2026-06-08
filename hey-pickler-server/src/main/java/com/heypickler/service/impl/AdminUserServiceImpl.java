package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.constant.RedisKey;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.AdminUserCreateRequest;
import com.heypickler.entity.AdminUser;
import com.heypickler.mapper.AdminUserMapper;
import com.heypickler.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Set<String> VALID_ROLES = Set.of("SUPER_ADMIN", "ADMIN", "OPERATOR");

    @Override
    public PageResult<AdminUser> listAdminUsers(int page, int size) {
        Page<AdminUser> pageParam = new Page<>(page, size);
        Page<AdminUser> resultPage = adminUserMapper.selectPage(pageParam, null);
        return PageResult.of(resultPage.getTotal(), page, size, resultPage.getRecords());
    }

    @Override
    public AdminUser getAdminUser(Long id) {
        AdminUser admin = adminUserMapper.selectById(id);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return admin;
    }

    @Override
    public Long createAdminUser(AdminUserCreateRequest request) {
        // Check duplicate username
        Long count = adminUserMapper.selectCount(
            new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }

        // Validate role
        if (!VALID_ROLES.contains(request.getRole())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "无效的角色");
        }

        AdminUser admin = new AdminUser();
        admin.setUsername(request.getUsername());
        admin.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        admin.setRole(request.getRole());
        admin.setStatus("ACTIVE");
        adminUserMapper.insert(admin);
        return admin.getId();
    }

    @Override
    public void updateAdminUser(Long id, String role, String status) {
        AdminUser admin = new AdminUser();
        admin.setId(id);
        if (StringUtils.hasText(role)) {
            if (!VALID_ROLES.contains(role)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "无效的角色");
            }
            admin.setRole(role);
        }
        if (StringUtils.hasText(status)) {
            admin.setStatus(status);
        }
        adminUserMapper.updateById(admin);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        AdminUser admin = adminUserMapper.selectById(id);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "管理员不存在");
        }
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        adminUserMapper.updateById(admin);

        // Force logout: delete Redis session
        redisTemplate.delete(RedisKey.adminSession(String.valueOf(id)));
    }
}
