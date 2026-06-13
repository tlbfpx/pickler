package com.heypickler.dto.admin;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EventUpdateRequest {
    private String type;
    private String title;
    private String bannerUrl;
    private String description;
    private String rules;
    private String location;
    private LocalDateTime eventTime;
    private LocalDateTime registrationDeadline;
    private Integer maxParticipants;
    private BigDecimal fee;
    private String prizes;
    private Integer minPoints;
    @Pattern(regexp = "^(DRAFT|OPEN|FULL|IN_PROGRESS|COMPLETED|CANCELLED)?$", message = "无效的赛事状态")
    private String status;
}
