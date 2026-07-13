package com.heypickler.controller.app;

import com.heypickler.common.result.Result;
import com.heypickler.service.DictService;
import com.heypickler.vo.DictBundleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/dict")
@RequiredArgsConstructor
@Tag(name = "小程序端-字典")
public class AppDictController {

    private final DictService dictService;

    @GetMapping("/bundle")
    @Operation(summary = "字典聚合 bundle（匿名，供小程序启动拉取）")
    public Result<DictBundleVO> bundle() {
        return Result.ok(dictService.getBundle());
    }
}
