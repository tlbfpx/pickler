package com.heypickler.controller.app;

import com.heypickler.common.annotation.PublicAnonymousAccess;
import com.heypickler.common.result.Result;
import com.heypickler.service.CourtService;
import com.heypickler.service.SlotService;
import com.heypickler.vo.CourtVO;
import com.heypickler.vo.SlotVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/app/courts")
@RequiredArgsConstructor
@Tag(name = "小程序-场地")
public class AppCourtController {
    private final CourtService courtService;
    private final SlotService slotService;

    @PublicAnonymousAccess
    @GetMapping @Operation(summary = "场地列表(按场馆,匿名)")
    public Result<List<CourtVO>> list(@RequestParam Long venueId) { return Result.ok(courtService.listByVenue(venueId)); }

    @PublicAnonymousAccess
    @GetMapping("/{id}/slots") @Operation(summary = "某场地某日可订格子+价格(匿名)")
    public Result<List<SlotVO>> slots(@PathVariable Long id,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(slotService.getCourtSlots(id, date));
    }
}
