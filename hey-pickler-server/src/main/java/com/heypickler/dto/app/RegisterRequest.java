package com.heypickler.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "比赛类型不能为空")
    @Pattern(regexp = "^(SINGLES|DOUBLES|MIXED)$", message = "无效的比赛类型")
    private String matchType;

    private Long partnerId;
}
