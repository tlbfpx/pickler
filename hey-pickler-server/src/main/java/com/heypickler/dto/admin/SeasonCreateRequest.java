package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SeasonCreateRequest {
    @NotBlank(message = "赛季类型不能为空")
    @Pattern(regexp = "^(STAR|PARTY)$", message = "赛季类型必须为 STAR 或 PARTY")
    private String type;

    @NotBlank(message = "赛季编码不能为空")
    private String code;

    @NotBlank(message = "赛季名称不能为空")
    private String name;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
}
