package com.heypickler.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class EventDetailVO extends EventVO {
    private String description;
    private String rules;
    private String prizes;
    private LocalDateTime registrationDeadline;
    private Integer minPoints;
    private String myRegistrationStatus;
}
