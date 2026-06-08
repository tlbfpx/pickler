package com.heypickler.dto.admin;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class PointEntryRequest {

    @NotEmpty(message = "Records cannot be empty")
    @Valid
    private List<PointRecordItem> records;

    @Data
    public static class PointRecordItem {
        private Long userId;

        @jakarta.validation.constraints.NotNull(message = "User ID cannot be null")
        private Integer points;

        private String reason;
    }
}
