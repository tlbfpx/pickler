package com.heypickler.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EventCreateRequest {
    @NotBlank(message = "赛事类型不能为空")
    private String type;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String bannerUrl;

    private String description;

    private String rules;

    @NotBlank(message = "地点不能为空")
    private String location;

    @NotNull(message = "比赛时间不能为空")
    private LocalDateTime eventTime;

    @NotNull(message = "报名截止时间不能为空")
    private LocalDateTime registrationDeadline;

    @NotNull(message = "最大参与人数不能为空")
    @Min(value = 1, message = "最大参与人数至少为1")
    private Integer maxParticipants;

    private BigDecimal fee = BigDecimal.ZERO;

    private String prizes;

    private String status = "DRAFT";
}
