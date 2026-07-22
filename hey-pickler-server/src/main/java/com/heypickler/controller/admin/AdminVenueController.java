package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.*;
import com.heypickler.service.VenueService;
import com.heypickler.vo.VenueDetailVO;
import com.heypickler.vo.VenueVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/venues")
@RequiredArgsConstructor
@Tag(name = "管理端-场馆管理")
public class AdminVenueController {
    private final VenueService venueService;

    @GetMapping @Operation(summary = "场馆列表")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<VenueVO>> list(VenueQueryRequest req) { return Result.ok(venueService.adminList(req)); }

    @GetMapping("/{id}") @Operation(summary = "场馆详情")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<VenueDetailVO> get(@PathVariable Long id) { return Result.ok(venueService.adminGet(id)); }

    @PostMapping @Operation(summary = "新建场馆")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Map<String, Object>> create(@RequestBody @Valid VenueCreateRequest req) {
        return Result.ok(Map.of("id", venueService.create(req)));
    }

    @PutMapping("/{id}") @Operation(summary = "更新场馆")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid VenueCreateRequest req) {
        venueService.update(id, req); return Result.ok();
    }

    @DeleteMapping("/{id}") @Operation(summary = "删除场馆")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> delete(@PathVariable Long id) { venueService.delete(id); return Result.ok(); }

    @PutMapping("/{id}/business-hours") @Operation(summary = "覆盖营业时间(7行)")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> replaceBusinessHours(@PathVariable Long id, @RequestBody @Valid VenueBusinessHourRequest req) {
        venueService.replaceBusinessHours(id, req); return Result.ok();
    }

    @PostMapping("/{id}/contacts") @Operation(summary = "新增联系方式")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Map<String, Object>> addContact(@PathVariable Long id, @RequestBody @Valid VenueContactRequest req) {
        return Result.ok(Map.of("id", venueService.addContact(id, req)));
    }
    @PutMapping("/contacts/{contactId}") @Operation(summary = "更新联系方式")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> updateContact(@PathVariable Long contactId, @RequestBody @Valid VenueContactRequest req) {
        venueService.updateContact(contactId, req); return Result.ok();
    }
    @DeleteMapping("/contacts/{contactId}") @Operation(summary = "删除联系方式")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> deleteContact(@PathVariable Long contactId) {
        venueService.deleteContact(contactId); return Result.ok();
    }
}
