package com.heypickler.controller.app;

import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.VenueQueryRequest;
import com.heypickler.service.VenueService;
import com.heypickler.vo.VenueDetailVO;
import com.heypickler.vo.VenueVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/venues")
@RequiredArgsConstructor
@Tag(name = "小程序-场馆")
public class AppVenueController {
    private final VenueService venueService;

    @GetMapping @Operation(summary = "场馆列表(匿名)")
    public Result<PageResult<VenueVO>> list(VenueQueryRequest req) { return Result.ok(venueService.appList(req)); }

    @GetMapping("/{id}") @Operation(summary = "场馆详情(匿名)")
    public Result<VenueDetailVO> get(@PathVariable Long id) { return Result.ok(venueService.appGet(id)); }
}
