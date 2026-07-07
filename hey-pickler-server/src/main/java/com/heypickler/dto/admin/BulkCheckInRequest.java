package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Loop-v14 — request body for bulk registration check-in.
 * See {@code openspec/changes/bulk-check-in/specs/bulk-check-in.md}.
 */
@Data
public class BulkCheckInRequest {

    @NotNull
    @Size(min = 1, max = 200, message = "registrationIds 数量需在 1-200 之间")
    private List<@Positive Long> registrationIds;
}
