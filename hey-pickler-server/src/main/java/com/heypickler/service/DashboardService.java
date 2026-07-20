package com.heypickler.service;

import com.heypickler.vo.AttendanceFunnelVO;
import com.heypickler.vo.CompareResultVO;
import com.heypickler.vo.DashboardTrendVO;
import com.heypickler.vo.TopEventVO;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘聚合服务（loop-v19 Dashboard Phase 1）。
 *
 * <p>所有 5 个方法都从 {@code AdminDashboardController} 抽出，可单测、可复用。
 * SNAPSHOT 保留既有 {@code LinkedHashMap} 形状以保证 {@code DashboardView.vue} 向后兼容。
 *
 * <p>缓存：5 min TTL，参见 {@link com.heypickler.common.constant.RedisKey} 的
 * {@code dashboard:*} 命名空间。
 */
public interface DashboardService {

    /**
     * 现有 {@code GET /api/admin/dashboard} 的全部输出（同 {@code LinkedHashMap} 形态，
     *  数字 KPI 多带 sibling {@code <key>DeltaPct} / {@code <key>DeltaAbs} 同比/环比）。
     *
     * @param bypassCache 仅 SUPER_ADMIN 传 true 可绕过 cache；普通角色忽略。
     */
    Map<String, Object> getSnapshot(boolean bypassCache);

    /** 时序：用户/报名/收入/活动 4 条按 day bucket 数组。{@code range} =
     *  {@code 7d|30d|90d|thisMonth|lastMonth|custom}（custom 时读 {@code from/to}）。 */
    DashboardTrendVO getTrends(String range, String from, String to, boolean bypassCache);

    /** 活动排行。{@code metric} = {@code registrations|revenue|fillRate}。
     *  {@code fillRate} 自动跳过 maxParticipants=0/null；{@code limit} 1..50。 */
    List<TopEventVO> getTopEvents(String metric, String range, String from, String to, int limit, boolean bypassCache);

    /** 出席漏斗：报名/签到/no-show%。registered=0 时 noShowRate=null。 */
    AttendanceFunnelVO getAttendance(String range, String from, String to, boolean bypassCache);

    /** 同比/环比。{@code previous=0,current>0} → deltaPct=null（避免除零与 Infinity）。 */
    CompareResultVO getCompare(String metric, String currentRange, String previousRange, boolean bypassCache);
}
