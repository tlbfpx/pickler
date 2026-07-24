package com.heypickler.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class VenueQueryRequest {
    private String keyword;
    private String status;

    @Min(value = 1)
    private int page = 1;

    @Min(value = 1)
    @Max(value = 100)
    private int size = 10;
}
