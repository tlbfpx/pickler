package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Event;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface EventMapper extends BaseMapper<Event> {

    /**
     * 悲观锁：事务内锁住 event 行，串行化并发 complete()，防双发分（review #4 P2）。
     * 必须在 @Transactional 内调用，行锁随事务提交/回滚释放。
     */
    @Select("SELECT * FROM event WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Event selectForUpdate(Long id);

    // ──────────────── Loop-v19 — Dashboard Phase 1 GROUP BY ────────────────

    /**
     * 按日分桶统计新增赛事（按 event.created_at，半开区间）。
     */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS cnt " +
            "FROM event " +
            "WHERE deleted_at IS NULL " +
            "AND created_at >= #{from} AND created_at < #{to} " +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date")
    List<Map<String, Object>> dailyNewEvents(@Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    /**
     * 区间内赛事增量（用于 KPI 同比分母）。
     */
    @Select("SELECT COUNT(*) FROM event " +
            "WHERE deleted_at IS NULL " +
            "AND created_at >= #{from} AND created_at < #{to}")
    long countNewInRange(@Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);

    /**
     * 按 event_time 截止到 toDate 的赛事数（用于滚动完赛率分母）。
     * 不限 created_at，统计的是历史上 event_time <= toDate 的未删赛事。
     */
    @Select("SELECT COUNT(*) FROM event " +
            "WHERE deleted_at IS NULL " +
            "AND event_time IS NOT NULL AND event_time < #{toDate}")
    long countEventsByEventTimeBefore(@Param("toDate") LocalDateTime toDate);

    /**
     * 滚动完赛率分子：截至 toDate、event_time <= toDate 且 status='COMPLETED' 的赛事数。
     */
    @Select("SELECT COUNT(*) FROM event " +
            "WHERE deleted_at IS NULL " +
            "AND status = 'COMPLETED' " +
            "AND event_time IS NOT NULL AND event_time < #{toDate}")
    long countCompletedByEventTimeBefore(@Param("toDate") LocalDateTime toDate);
}
