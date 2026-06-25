package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.GroupingRequest;
import com.heypickler.service.GroupingService;
import com.heypickler.vo.GroupVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/events/{eventId}/grouping")
@RequiredArgsConstructor
@Tag(name = "管理后台-分组")
public class AdminGroupingController {

    private final GroupingService groupingService;

    @PostMapping
    @Operation(summary = "执行分组（返回预览，未锁定）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<GroupVO>> group(
            @PathVariable Long eventId,
            @Valid @RequestBody GroupingRequest req) {
        return Result.ok(groupingService.group(eventId, req.getStrategy(), req.getGroupCount()));
    }

    @GetMapping
    @Operation(summary = "查看分组结果")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<GroupVO>> getGroups(@PathVariable Long eventId) {
        return Result.ok(groupingService.getGroups(eventId));
    }

    @PutMapping("/assignments/{assignmentId}")
    @Operation(summary = "微调：将某分组成员换到另一组（仅未锁定）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> reassign(
            @PathVariable Long eventId,
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Long> body) {
        groupingService.reassign(eventId, assignmentId, body.get("targetGroupId"));
        return Result.ok();
    }

    @PostMapping("/lock")
    @Operation(summary = "锁定分组")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> lock(@PathVariable Long eventId) {
        groupingService.lock(eventId);
        return Result.ok();
    }

    @PostMapping("/unlock")
    @Operation(summary = "解锁分组（清空当前分组并重新开放报名）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> unlock(@PathVariable Long eventId) {
        groupingService.unlock(eventId);
        return Result.ok();
    }
}
