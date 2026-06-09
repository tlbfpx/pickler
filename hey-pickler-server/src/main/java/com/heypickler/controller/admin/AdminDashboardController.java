package com.heypickler.controller.admin;

import com.heypickler.common.result.Result;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "管理端-控制台")
public class AdminDashboardController {

    private final UserMapper userMapper;
    private final EventMapper eventMapper;
    private final RegistrationMapper registrationMapper;

    @GetMapping
    @Operation(summary = "控制台统计数据")
    public Result<Map<String, Object>> getStats() {
        long totalUsers = userMapper.selectCount(null);
        long activeEvents = eventMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.heypickler.entity.Event>()
                        .isNull("deleted_at")
                        .in("status", "OPEN", "UPCOMING", "ONGOING"));
        long recentRegistrations = registrationMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.heypickler.entity.Registration>()
                        .ge("created_at", java.time.LocalDateTime.now().minusDays(7)));

        return Result.ok(Map.of(
                "totalUsers", totalUsers,
                "activeEvents", activeEvents,
                "recentRegistrations", recentRegistrations
        ));
    }
}
