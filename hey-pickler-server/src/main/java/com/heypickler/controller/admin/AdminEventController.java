package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.PointSource;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.EventCreateRequest;
import com.heypickler.dto.admin.EventUpdateRequest;
import com.heypickler.dto.admin.PlacementPointsRequest;
import com.heypickler.dto.admin.PointEntryRequest;
import com.heypickler.entity.EventPlacementPoints;
import com.heypickler.service.EventService;
import com.heypickler.service.PlacementService;
import com.heypickler.service.PointService;
import com.heypickler.service.dto.PointEntry;
import com.heypickler.vo.EventParticipantVO;
import com.heypickler.vo.EventVO;
import com.heypickler.vo.PlacementDetailVO;
import com.heypickler.vo.PlacementPointsVO;
import com.heypickler.vo.RegistrationVO;
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
    private final PointService pointService;
    private final PlacementService placementService;

    @GetMapping
    @Operation(summary = "赛事列表（管理后台）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<EventVO>> listEvents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(eventService.adminListEvents(type, status, keyword, location, startTime, endTime, page, size));
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

    @PatchMapping("/{id}/status")
    @Operation(summary = "变更赛事状态")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String targetStatus = body.get("status");
        EventUpdateRequest req = new EventUpdateRequest();
        req.setStatus(targetStatus);
        eventService.updateEvent(id, req);
        return Result.ok();
    }

    @GetMapping("/{id}/participants")
    @Operation(summary = "获取赛事参赛者列表")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<EventParticipantVO>> getParticipants(@PathVariable Long id) {
        return Result.ok(eventService.getParticipants(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取赛事详情（管理后台）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<EventVO> detail(@PathVariable Long id) {
        return Result.ok(eventService.getEventDetail(id));
    }

    @PostMapping("/{id}/points")
    @Operation(summary = "录入积分")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> enterPoints(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody PointEntryRequest req) {
        Long adminId = (Long) request.getAttribute("adminId");
        // 关联赛事发分：强制 MANUAL 来源，不读请求体 source
        pointService.enterPoints(id, req.getType(), toPointEntries(req), PointSource.MANUAL, adminId);
        return Result.ok();
    }

    @GetMapping("/{eventId}/registrations")
    @Operation(summary = "获取赛事报名列表（分页）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<RegistrationVO>> getRegistrations(
            @PathVariable Long eventId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String matchType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(eventService.getRegistrations(eventId, status, matchType, page, size));
    }

    @PatchMapping("/{eventId}/registrations/{registrationId}/status")
    @Operation(summary = "修改报名状态（签到/取消）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> updateRegistrationStatus(
            @PathVariable Long eventId,
            @PathVariable Long registrationId,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        eventService.updateRegistrationStatus(eventId, registrationId, status);
        return Result.ok();
    }

    private java.util.List<PointEntry> toPointEntries(PointEntryRequest req) {
        return req.getRecords().stream()
                .map(item -> new PointEntry(item.getUserId(), item.getPoints(), item.getReason()))
                .collect(java.util.stream.Collectors.toList());
    }

    @GetMapping("/{id}/placement-points")
    @Operation(summary = "查看赛事名次加分表（自定义或默认）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PlacementPointsVO> getPlacementPoints(@PathVariable Long id) {
        return Result.ok(placementService.getPoints(id));
    }

    @PutMapping("/{id}/placement-points")
    @Operation(summary = "配置赛事名次加分表（仅在赛事未完成时可设置）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> setPlacementPoints(
            @PathVariable Long id,
            @Valid @RequestBody PlacementPointsRequest req) {
        EventPlacementPoints override = new EventPlacementPoints();
        override.setPointsMap(req.getPoints());
        placementService.setPoints(id, override);
        return Result.ok();
    }

    @DeleteMapping("/{id}/placement-points")
    @Operation(summary = "清除赛事名次加分表，恢复为系统默认")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> clearPlacementPoints(@PathVariable Long id) {
        placementService.clearPoints(id);
        return Result.ok();
    }

    @GetMapping("/{id}/placements")
    @Operation(summary = "查询赛事 PLACEMENT 发分明细（按积分降序）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<PlacementDetailVO>> getPlacements(@PathVariable Long id) {
        return Result.ok(placementService.listByEventId(id));
    }

    // ──────────────── Loop-v13 — operational summary endpoint ────────────────

    @GetMapping("/{id}/summary")
    @Operation(summary = "赛事运营汇总（报名/签到/队伍/比赛/费用/可转换状态）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<com.heypickler.vo.EventSummaryVO> getEventSummary(@PathVariable Long id) {
        return Result.ok(eventService.getEventSummary(id));
    }
}
