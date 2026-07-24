package com.heypickler.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class CourtPricingBandRequest {

    @NotBlank
    @Pattern(regexp = "^(WEEKDAY|WEEKEND|ALL)$")
    private String dayType;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal price;
}
