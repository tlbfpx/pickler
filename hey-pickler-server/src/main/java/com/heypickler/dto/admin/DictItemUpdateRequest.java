package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DictItemUpdateRequest {
    @NotBlank
    private String itemKey;        // 仅用于定位行，不会写回
    private String itemLabel;
    private String itemColor;
    private Integer sort;
    private String status;         // ENABLED / DISABLED
}
