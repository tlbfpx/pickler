package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BannerCreateRequest {
    @NotBlank(message = "图片地址不能为空")
    private String imageUrl;
    private String linkUrl;
    private Integer sortOrder;
    private String status;
}
