package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class PlacementPointsRequest {

    @NotNull(message = "积分表不能为空")
    @NotEmpty(message = "积分表不能为空")
    private Map<Integer, Integer> points;
}