package com.heypickler.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BannerCreateRequest {
    @NotBlank(message = "图片地址不能为空")
    @Pattern(regexp = "^https?://.*", message = "图片地址必须为合法URL")
    private String imageUrl;

    @Pattern(regexp = "^(https?://.*)?$", message = "跳转链接必须为合法URL")
    private String linkUrl;

    @NotNull(message = "排序不能为空")
    @Min(value = 0, message = "排序不能为负数")
    private Integer sortOrder;

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "无效的状态")
    private String status;
}
