package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BanRequest {
    @NotBlank(message = "封禁原因不能为空")
    private String reason;

    private LocalDateTime banUntil;
}
