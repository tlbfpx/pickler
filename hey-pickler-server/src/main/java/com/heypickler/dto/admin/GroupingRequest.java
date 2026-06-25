package com.heypickler.dto.admin;

import com.heypickler.common.enums.GroupingStrategyType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupingRequest {

    @NotNull(message = "分组策略不能为空")
    private GroupingStrategyType strategy;

    @NotNull(message = "组数不能为空")
    @Min(value = 1, message = "组数必须大于0")
    private Integer groupCount;
}
