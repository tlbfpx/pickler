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

    private String type;

    @Data
    public static class PointRecordItem {
        @jakarta.validation.constraints.NotNull(message = "User ID cannot be null")
        private Long userId;

        @jakarta.validation.constraints.NotNull(message = "Points cannot be null")
        private Integer points;

        private String reason;
    }
}
