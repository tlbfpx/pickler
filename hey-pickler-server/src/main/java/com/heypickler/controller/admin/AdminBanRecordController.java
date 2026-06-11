package com.heypickler.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.entity.AdminUser;
import com.heypickler.entity.BanRecord;
import com.heypickler.entity.User;
import com.heypickler.mapper.AdminUserMapper;
import com.heypickler.mapper.BanRecordMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.common.util.AesUtil;
import com.heypickler.vo.BanRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/ban-records")
@RequiredArgsConstructor
@Tag(name = "管理端-操作日志")
public class AdminBanRecordController {

    private final BanRecordMapper banRecordMapper;
    private final UserMapper userMapper;
    private final AdminUserMapper adminUserMapper;
    private final AesUtil aesUtil;

    @GetMapping
    @Operation(summary = "封禁操作日志列表")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<BanRecordVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action) {

        LambdaQueryWrapper<BanRecord> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(BanRecord::getUserId, userId);
        }
        if (StringUtils.hasText(action)) {
            wrapper.eq(BanRecord::getAction, action);
        }
        wrapper.orderByDesc(BanRecord::getCreatedAt);

        Page<BanRecord> recordPage = banRecordMapper.selectPage(new Page<>(page, size), wrapper);

        if (recordPage.getRecords().isEmpty()) {
            return Result.ok(PageResult.of(recordPage.getTotal(), page, size, List.of()));
        }

        // Batch load users
        List<Long> userIds = recordPage.getRecords().stream()
                .map(BanRecord::getUserId).distinct().toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Batch load operators
        List<Long> operatorIds = recordPage.getRecords().stream()
                .map(BanRecord::getOperatorId).distinct().toList();
        Map<Long, AdminUser> operatorMap = adminUserMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(AdminUser::getId, a -> a));

        List<BanRecordVO> vos = recordPage.getRecords().stream().map(record -> {
            BanRecordVO vo = new BanRecordVO();
            vo.setId(record.getId());
            vo.setUserId(record.getUserId());
            vo.setAction(record.getAction());
            vo.setReason(record.getReason());
            vo.setBanUntil(record.getBanUntil());
            vo.setCreatedAt(record.getCreatedAt());
            vo.setOperatorId(record.getOperatorId());

            User user = userMap.get(record.getUserId());
            if (user != null) {
                vo.setUserNickname(user.getNickname());
                vo.setUserPhone(safeDecrypt(user.getPhone()));
            }

            AdminUser operator = operatorMap.get(record.getOperatorId());
            if (operator != null) {
                vo.setOperatorName(operator.getUsername());
            }

            return vo;
        }).toList();

        return Result.ok(PageResult.of(recordPage.getTotal(), page, size, vos));
    }

    private String safeDecrypt(String value) {
        if (value == null) return null;
        try {
            return aesUtil.decrypt(value);
        } catch (Exception e) {
            return value;
        }
    }
}
