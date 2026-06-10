package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetRequest {
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度须为8-128位")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "密码须包含字母和数字")
    private String newPassword;
}
