package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Team;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TeamMapper extends BaseMapper<Team> {

    /**
     * Loop-v13 — count teams grouped by status for an event.
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM team " +
            "WHERE event_id = #{eventId} " +
            "GROUP BY status")
    List<Map<String, Object>> countByEventGroupedByStatus(@Param("eventId") Long eventId);
}
