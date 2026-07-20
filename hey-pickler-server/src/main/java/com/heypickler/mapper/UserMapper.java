package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * Loop-v19 Dashboard Phase 1：按日分桶统计新增用户。
     * 返回 [{date=YYYY-MM-DD, cnt=N}]，缺失日期不会补零（应用层补）。半开区间 [from, to)。
     */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS cnt " +
            "FROM user " +
            "WHERE created_at >= #{from} AND created_at < #{to} " +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date")
    List<Map<String, Object>> dailyNewUsers(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    /**
     * 区间内注册用户增量（KPI 同比分母）。半开区间 [from, to)。
     */
    @Select("SELECT COUNT(*) FROM user " +
            "WHERE created_at >= #{from} AND created_at < #{to}")
    long countNewInRange(@Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);
}
