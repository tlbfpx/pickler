package com.heypickler.controller.app;

import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.app.UserUpdateRequest;
import com.heypickler.service.UserService;
import com.heypickler.vo.MyEventVO;
import com.heypickler.vo.PointRecordVO;
import com.heypickler.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/user")
@RequiredArgsConstructor
@Tag(name = "小程序-用户")
public class AppUserController {

    private final UserService userService;

    @GetMapping("/profile")
    @Operation(summary = "获取用户信息")
    public Result<UserProfileVO> getProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    @Operation(summary = "更新用户信息")
    public Result<Void> updateProfile(HttpServletRequest request,
                                       @RequestBody UserUpdateRequest userUpdateRequest) {
        Long userId = (Long) request.getAttribute("userId");
        userService.updateProfile(userId, userUpdateRequest);
        return Result.ok();
    }

    @GetMapping("/events")
    @Operation(summary = "获取我的活动列表")
    public Result<PageResult<MyEventVO>> getMyEvents(HttpServletRequest request,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userService.getMyEvents(userId, type, page, size));
    }

    @GetMapping("/points")
    @Operation(summary = "获取积分记录")
    public Result<PageResult<PointRecordVO>> getPointHistory(HttpServletRequest request,
                                                              @RequestParam(required = false) String type,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userService.getPointHistory(userId, type, page, size));
    }
}
