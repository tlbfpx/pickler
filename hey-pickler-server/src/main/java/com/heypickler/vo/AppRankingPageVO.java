package com.heypickler.vo;

import com.heypickler.common.result.PageResult;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * App 排行榜分页响应：榜单分页（list/total/page/size 平铺在顶层，兼容 wxapp {@code res.data.list} 契约）
 * + 当前 track 段位名映射（per-track，供段位筛选 tab 渲染，避免 PARTY 轨 fallback 到 STAR 称号）。
 *
 * <p>与 admin {@link RankingPageVO} 的区别：不含段位分布/色图/icon 图/赛季元信息（app 排名页不需要），
 * 且 list 平铺顶层（RankingPageVO 嵌套在 {@code page} 字段内，直接复用会破坏 app 现有 {@code res.data.list} 契约）。
 */
@Data
public class AppRankingPageVO {
    private long total;
    private int page;
    private int size;
    private List<RankingVO> list;
    /** 当前 track 全 6 档 tier_code→段位名，per-track（见 {@code RankingService.tierNameMap}） */
    private Map<String, String> tierNameMap;

    public static AppRankingPageVO of(PageResult<RankingVO> page, Map<String, String> tierNameMap) {
        AppRankingPageVO vo = new AppRankingPageVO();
        vo.setTotal(page.getTotal());
        vo.setPage(page.getPage());
        vo.setSize(page.getSize());
        vo.setList(page.getList());
        vo.setTierNameMap(tierNameMap);
        return vo;
    }
}
