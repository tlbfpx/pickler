package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.BookingForceCancelRequest;
import com.heypickler.dto.admin.BookingQueryRequest;
import com.heypickler.service.BookingService;
import com.heypickler.vo.BookingAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
@Tag(name = "管理端-预约管理")
public class AdminBookingController {
    private final BookingService bookingService;

    @GetMapping
    @Operation(summary = "预约列表(分页+筛选)")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<BookingAdminVO>> list(BookingQueryRequest q) {
        return Result.ok(bookingService.listAdmin(q));
    }

    @GetMapping("/{id}")
    @Operation(summary = "预约详情")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<BookingAdminVO> get(@PathVariable Long id) { return Result.ok(bookingService.getAdmin(id)); }

    @PostMapping("/{id}/complete")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> complete(@PathVariable Long id) { bookingService.complete(id); return Result.ok(); }

    @PostMapping("/{id}/no-show")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> noShow(@PathVariable Long id) { bookingService.markNoShow(id); return Result.ok(); }

    @PostMapping("/{id}/cancel")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> cancel(@PathVariable Long id, @RequestBody(required = false) @Valid BookingForceCancelRequest body) {
        bookingService.forceCancel(id, body);
        return Result.ok();
    }
}