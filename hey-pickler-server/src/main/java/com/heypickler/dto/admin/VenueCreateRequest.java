package com.heypickler.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VenueCreateRequest {
    @NotBlank(message = "场馆名不能为空")
    @Size(max = 128, message = "场馆名过长")
    private String name;

    @NotBlank(message = "地址不能为空")
    @Size(max = 256, message = "地址过长")
    private String address;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String coverUrl;
    private String description;

    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "无效的状态")
    private String status;

    @Min(value = 1, message = "可订窗口至少 1 天")
    private Integer bookingLeadDays;
}
