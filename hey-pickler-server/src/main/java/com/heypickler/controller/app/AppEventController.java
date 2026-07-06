package com.heypickler.controller.app;

import com.heypickler.common.annotation.PublicAnonymousAccess;
import com.heypickler.common.annotation.RequireAppUser;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.app.RegisterRequest;
import com.heypickler.service.EventService;
import com.heypickler.service.TeamService;
import com.heypickler.vo.EventDetailVO;
import com.heypickler.vo.EventResultVO;
import com.heypickler.vo.EventVO;
import com.heypickler.vo.TeamVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app/events")
@RequiredArgsConstructor
@Tag(name = "小程序-赛事")
public class AppEventController {

    private final EventService eventService;
    private final TeamService teamService;

    @GetMapping
    @PublicAnonymousAccess
    @Operation(summary = "赛事列表")
    public Result<PageResult<EventVO>> listEvents(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(eventService.listEvents(type, status, page, size));
    }

    @GetMapping("/{id}")
    @PublicAnonymousAccess
    @Operation(summary = "赛事详情")
    public Result<EventDetailVO> getEventDetail(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(eventService.getEventDetail(id, userId));
    }

    @PostMapping("/{id}/register")
    @RequireAppUser
    @Operation(summary = "报名赛事")
    public Result<Void> register(
            @PathVariable Long id,
            HttpServletRequest request,
            @Valid @RequestBody RegisterRequest req) {
        Long userId = (Long) request.getAttribute("userId");
        eventService.register(userId, id, req);
        return Result.ok();
    }

    @PostMapping("/{id}/cancel")
    @RequireAppUser
    @Operation(summary = "取消报名")
    public Result<Void> cancelRegistration(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        eventService.cancelRegistration(userId, id);
        return Result.ok();
    }

    @GetMapping("/{id}/my-team")
    @RequireAppUser
    @Operation(summary = "查询当前用户在该赛事的队伍状态")
    public Result<TeamVO> getMyTeam(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(teamService.toVO(teamService.getMyTeam(id, userId)));
    }

    @GetMapping("/{id}/results")
    @PublicAnonymousAccess
    @Operation(summary = "赛事成绩榜")
    public Result<List<EventResultVO>> getResults(@PathVariable Long id) {
        return Result.ok(eventService.getEventResults(id));
    }
}
