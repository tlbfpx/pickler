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
import com.heypickler.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Set<String> VALID_ROLES = Set.of("SUPER_ADMIN", "ADMIN", "OPERATOR");
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "INACTIVE");

    @Override
    public PageResult<AdminUserVO> listAdminUsers(int page, int size) {
        Page<AdminUser> pageParam = new Page<>(page, size);
        Page<AdminUser> resultPage = adminUserMapper.selectPage(pageParam, null);
        var voList = resultPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(resultPage.getTotal(), page, size, voList);
    }

    @Override
    public AdminUserVO getAdminUser(Long id) {
        AdminUser admin = adminUserMapper.selectById(id);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toVO(admin);
    }

    @Override
    public Long createAdminUser(AdminUserCreateRequest request) {
        Long count = adminUserMapper.selectCount(
            new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }

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
            if (!VALID_STATUSES.contains(status)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "无效的状态");
            }
            admin.setStatus(status);
        }
        adminUserMapper.updateById(admin);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 8) {
            throw new BizException(ErrorCode.PARAM_ERROR, "密码长度不能少于8位");
        }
        AdminUser admin = adminUserMapper.selectById(id);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "管理员不存在");
        }
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        adminUserMapper.updateById(admin);

        redisTemplate.delete(RedisKey.adminSession(String.valueOf(id)));
    }

    @Override
    public void deleteAdminUser(Long id, Long currentAdminId) {
        if (id.equals(currentAdminId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能删除自己");
        }
        AdminUser admin = adminUserMapper.selectById(id);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "管理员不存在");
        }
        if ("SUPER_ADMIN".equals(admin.getRole())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "超级管理员不能被删除");
        }
        adminUserMapper.deleteById(id);
        redisTemplate.delete(RedisKey.adminSession(String.valueOf(id)));
    }

    private AdminUserVO toVO(AdminUser entity) {
        AdminUserVO vo = new AdminUserVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
