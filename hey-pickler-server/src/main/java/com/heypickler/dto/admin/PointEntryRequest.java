package com.heypickler.dto.admin;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class PointEntryRequest {

    private Long eventId;

    private String type;

    @NotEmpty(message = "积分记录不能为空")
    @Valid
    private List<PointRecordItem> records;

    @Data
    public static class PointRecordItem {
        @jakarta.validation.constraints.NotNull(message = "用户ID不能为空")
        private Long userId;

        @jakarta.validation.constraints.NotNull(message = "积分不能为空")
        private Integer points;

        @jakarta.validation.constraints.NotBlank(message = "原因不能为空")
        private String reason;
    }
}
