package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Registration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
}
