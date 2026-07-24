package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VenueContactRequest {

    @NotBlank
    @Pattern(regexp = "^(PHONE|WECHAT|LANDLINE|EMAIL)$")
    private String type;

    @NotBlank
    @Size(max = 128)
    private String value;

    @Size(max = 64)
    private String label;

    private Integer sortOrder;
}
