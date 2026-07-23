package com.heypickler.controller.app;

import com.heypickler.common.annotation.RequireAppUser;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.app.BookingCreateRequest;
import com.heypickler.service.BookingService;
import com.heypickler.vo.BookingCreateResultVO;
import com.heypickler.vo.BookingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/bookings")
@RequiredArgsConstructor
@Tag(name = "小程序-预约")
public class AppBookingController {
    private final BookingService bookingService;

    @PostMapping
    @RequireAppUser
    @Operation(summary = "下单(自助预约)")
    public Result<BookingCreateResultVO> create(HttpServletRequest req, @RequestBody @Valid BookingCreateRequest body) {
        return Result.ok(bookingService.create(req, body));
    }

    @GetMapping("/my")
    @RequireAppUser
    @Operation(summary = "我的预约")
    public Result<PageResult<BookingVO>> my(HttpServletRequest req,
                                            @RequestParam(defaultValue = "upcoming") String group,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(bookingService.listMine(req, group, page, size));
    }

    @PostMapping("/{id}/cancel")
    @RequireAppUser
    @Operation(summary = "取消预约(截止前)")
    public Result<Void> cancel(HttpServletRequest req, @PathVariable Long id) {
        bookingService.cancelMine(req, id);
        return Result.ok();
    }
}