package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Registration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
