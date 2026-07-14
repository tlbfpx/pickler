package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.DictItemUpdateRequest;
import com.heypickler.service.DictService;
import com.heypickler.vo.DictBundleVO;
import com.heypickler.vo.SysDictItemVO;
import com.heypickler.vo.SysDictVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dict")
@RequiredArgsConstructor
@Tag(name = "管理端-字典管理")
public class AdminDictController {

    private final DictService dictService;

    @GetMapping
    @Operation(summary = "字典目录列表")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<SysDictVO>> listDicts() {
        return Result.ok(dictService.listDicts());
    }

    @GetMapping("/{dictCode}/items")
    @Operation(summary = "某字典的项列表")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<SysDictItemVO>> listItems(@PathVariable String dictCode) {
        return Result.ok(dictService.listItems(dictCode));
    }

    @PutMapping("/{dictCode}/items")
    @Operation(summary = "批量更新字典项（label/color/sort/status）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> updateItems(@PathVariable String dictCode,
                                    @RequestBody @Valid List<DictItemUpdateRequest> items) {
        dictService.updateItems(dictCode, items);
        return Result.ok();
    }

    @GetMapping("/bundle")
    @Operation(summary = "聚合 bundle（全部字典+items，带版本号）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<DictBundleVO> bundle() {
        return Result.ok(dictService.getBundle());
    }

    @GetMapping("/version")
    @Operation(summary = "全局字典版本号")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<Map<String, Long>> version() {
        return Result.ok(Map.of("version", dictService.getVersion()));
    }
}
