package com.heypickler.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发分明细：单条 (用户, 积分, 原因)。
 * 由 PointService.enterPoints 消费。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointEntry {
    private Long userId;
    private Integer points;
    private String reason;
}
