package com.heypickler.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogQuery {
    private Long operatorId;
    private String module;
    private String action;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
