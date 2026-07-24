package com.heypickler.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookingForceCancelRequest {
    @Size(max = 256) private String reason;
}
