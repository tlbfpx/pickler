package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserCreateRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度须为3-50位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度须为8-128位")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "密码须包含字母和数字")
    private String password;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(SUPER_ADMIN|ADMIN|OPERATOR)$", message = "无效的角色")
    private String role;
}
