package com.heypickler.vo;

import com.heypickler.common.result.PageResult;
import lombok.Data;

import java.util.Map;

/**
 * 排名工作台分页响应：榜单分页 + 段位分布 + 当前/所选赛季元信息。
 * 当前赛季与归档赛季查询统一返回此结构，前端按 seasonStatus 决定是否禁用写操作。
 */
@Data
public class RankingPageVO {
    private PageResult<RankingVO> page;
    /** 段位分布 {BRONZE: 12, SILVER: 5, ...}，仅含有行的段位 */
    private Map<String, Integer> tierDistribution;
    /** 段位色映射 {BRONZE: #A56C2C, ...}，当前 track 全 6 档，供前端染色图例/徽章 */
    private Map<String, String> tierColorMap;
    /** 段位名映射 {BRONZE: 青铜/见习球友, ...}，当前 track 全 6 档（per-track，供筛选/分布条标签，避免单套 TIER_NAME fallback） */
    private Map<String, String> tierNameMap;
    /** 段位图标映射 {BRONZE: 🥉/🌟, ...}，当前 track 全 6 档（per-track，供前端图例/徽章渲染） */
    private Map<String, String> tierIconMap;
    private String seasonCode;
    private String seasonName;
    /** CURRENT | ARCHIVED */
    private String seasonStatus;
}
