package com.heypickler.dto.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "比赛类型不能为空")
    private String matchType;

    private Long partnerId;
}
