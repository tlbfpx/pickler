package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单条 PLACEMENT 发分明细（详情页「发分」Tab 使用）。
 * rank 按 points DESC, id ASC 排序后由 1 开始编号。
 */
@Data
public class PlacementDetailVO {
    /** 1-based 排名 */
    private Integer rank;
    /** 选手用户 ID */
    private Long userId;
    /** 选手昵称（可能为 null：用户已注销时） */
    private String nickname;
    /** 发放积分数 */
    private Integer points;
    /** 发分原因（赛事名+名次文案） */
    private String reason;
    /** 发分时间（ISO 字符串由 Jackson 默认序列化） */
    private LocalDateTime createdAt;
}