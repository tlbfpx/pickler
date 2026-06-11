package com.heypickler.dto.admin;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminUserUpdateRequest {
    @Pattern(regexp = "^(SUPER_ADMIN|ADMIN|OPERATOR)$", message = "无效的角色")
    private String role;

    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "无效的状态")
    private String status;
}
