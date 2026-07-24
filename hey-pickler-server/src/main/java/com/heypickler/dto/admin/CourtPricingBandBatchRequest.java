package com.heypickler.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CourtPricingBandBatchRequest {

    @NotEmpty(message = "定价带不能为空")
    @Valid
    private List<CourtPricingBandRequest> bands;
}
