package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.admin.EventUpdateRequest;
import com.heypickler.dto.admin.PointEntryRequest;
import com.heypickler.service.EventService;
import com.heypickler.service.RankingService;
import com.heypickler.vo.EventParticipantVO;
import com.heypickler.vo.EventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
@Tag(name = "管理后台-赛事")
public class AdminEventController {

    private final EventService eventService;
    private final RankingService rankingService;

    @GetMapping
    @Operation(summary = "赛事列表（管理后台）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<EventVO>> listEvents(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(eventService.adminListEvents(type, status, page, size));
    }

    @PostMapping
    @Operation(summary = "创建赛事")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Map<String, Long>> createEvent(
            HttpServletRequest request,
            @Valid @RequestBody EventCreateRequest req) {
        Long adminId = (Long) request.getAttribute("adminId");
        Long eventId = eventService.createEvent(req, adminId);
        return Result.ok(Map.of("id", eventId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新赛事")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody EventUpdateRequest req) {
        eventService.updateEvent(id, req);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除赛事")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return Result.ok();
    }

    @GetMapping("/{id}/participants")
    @Operation(summary = "获取赛事参赛者列表")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<EventParticipantVO>> getParticipants(@PathVariable Long id) {
        return Result.ok(eventService.getParticipants(id));
    }

    @PostMapping("/{id}/points")
    @Operation(summary = "录入积分")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> enterPoints(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody PointEntryRequest req) {
        Long adminId = (Long) request.getAttribute("adminId");
        rankingService.enterPoints(id, req, adminId);
        return Result.ok();
    }
}
