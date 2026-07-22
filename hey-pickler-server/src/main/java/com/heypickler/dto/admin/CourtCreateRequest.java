package com.heypickler.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CourtCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    @Pattern(regexp = "^(INDOOR|OUTDOOR)$")
    private String courtType;

    @Min(value = 15)
    @Max(value = 240)
    private Integer slotMinutes; // 15min..4h

    @Pattern(regexp = "^(OPEN|CLOSED|MAINTENANCE)$")
    private String status;

    private Integer sortOrder;

    private Long venueId; // create 时必填;update 忽略
}
