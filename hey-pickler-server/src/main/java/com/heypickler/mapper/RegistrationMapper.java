package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Registration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {

    /**
     * Loop-v13 — count registrations grouped by status for an event.
     * Returns one row per distinct status, e.g. {status=REGISTERED, cnt=18}.
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM registration " +
            "WHERE event_id = #{eventId} " +
            "GROUP BY status")
    List<Map<String, Object>> countByEventGroupedByStatus(@Param("eventId") Long eventId);

    // ──────────────── Loop-v14 — bulk check-in ────────────────

    /**
     * Fetch current status for the given ids under an event. Returns rows
     * with id + status fields only; the rest of Registration is null.
     */
    @Select("SELECT id, status FROM registration " +
            "WHERE event_id = #{eventId} AND id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>" +
            "#{i}</foreach>")
    List<Registration> findByEventAndIds(@Param("eventId") Long eventId,
                                        @Param("ids") List<Long> ids);

    /**
     * Update status to CHECKED_IN for the given registration ids. Returns
     * the number of affected rows. Idempotent on already-CHECKED_IN rows
     * (status='CHECKED_IN' is preserved).
     */
    @Update("UPDATE registration SET status = 'CHECKED_IN' " +
            "WHERE id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>" +
            "#{i}</foreach> " +
            "AND status = 'REGISTERED'")
    int updateStatusToCheckedIn(@Param("ids") List<Long> ids);

    // ──────────────── Loop-v19 — Dashboard Phase 1 GROUP BY ────────────────

    /**
     * 按日分桶统计有效报名（REGISTERED，WITHDRAWN/CANCELLED 排除）。
     * 返回 [{date=YYYY-MM-DD, cnt=N}]。
     */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS cnt " +
            "FROM registration " +
            "WHERE status = 'REGISTERED' " +
            "AND created_at >= #{from} AND created_at < #{to} " +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date")
    List<Map<String, Object>> dailyRegistrations(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

    /**
     * 区间内有效报名总数（REGISTERED 状态，排除 WITHDRAWN/CANCELLED）。
     * 用于 KPI 总数 / 同比分母。
     */
    @Select("SELECT COUNT(*) FROM registration " +
            "WHERE status = 'REGISTERED' " +
            "AND created_at >= #{from} AND created_at < #{to}")
    long countActiveInRange(@Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    /**
     * 区间内 revenue = SUM(event.fee) JOIN event，registered-only，半开区间 [from, to)。
     * SQL JOIN 替代 controller 现存的全表 selectList + 内存 join feeEventMap（loop-v19）。
     */
    @Select("SELECT COALESCE(SUM(e.fee), 0) " +
            "FROM registration r JOIN event e ON e.id = r.event_id " +
            "WHERE r.status = 'REGISTERED' " +
            "AND r.created_at >= #{from} AND r.created_at < #{to} " +
            "AND e.deleted_at IS NULL")
    java.math.BigDecimal revenueInRange(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    /**
     * 已签到（CHECKED_IN）的有效报名数，半开区间 [from, to)。
     * 用于出席漏斗。
     */
    @Select("SELECT COUNT(*) FROM registration " +
            "WHERE status = 'CHECKED_IN' " +
            "AND created_at >= #{from} AND created_at < #{to}")
    long countCheckedInInRange(@Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    /**
     * 区间内 Top 活动排行（按 REGISTERED 报名数排序）。{@code maxParticipants=0}
     * 行交给应用层过滤（在 spec R3 fillRate 路径）。半开区间 [from, to)。
     */
    @Select("SELECT e.id AS eventId, e.title AS title, " +
            "       e.max_participants AS maxParticipants, " +
            "       e.current_participants AS currentParticipants, " +
            "       COUNT(*) AS value " +
            "FROM registration r JOIN event e ON e.id = r.event_id " +
            "WHERE r.status = 'REGISTERED' " +
            "AND r.created_at >= #{from} AND r.created_at < #{to} " +
            "AND e.deleted_at IS NULL " +
            "GROUP BY e.id, e.title, e.max_participants, e.current_participants " +
            "ORDER BY value DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> topEventsByRegistrations(@Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to,
                                                      @Param("limit") int limit);

    /**
     * 区间内 Top 活动排行（按 revenue 排序，SUM fee）。
     */
    @Select("SELECT e.id AS eventId, e.title AS title, " +
            "       e.max_participants AS maxParticipants, " +
            "       e.current_participants AS currentParticipants, " +
            "       COALESCE(SUM(e.fee), 0) AS value " +
            "FROM registration r JOIN event e ON e.id = r.event_id " +
            "WHERE r.status = 'REGISTERED' " +
            "AND r.created_at >= #{from} AND r.created_at < #{to} " +
            "AND e.deleted_at IS NULL " +
            "GROUP BY e.id, e.title, e.max_participants, e.current_participants " +
            "ORDER BY value DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> topEventsByRevenue(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to,
                                                  @Param("limit") int limit);
}
