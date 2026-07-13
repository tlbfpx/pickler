package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Ranking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface RankingMapper extends BaseMapper<Ranking> {

    /**
     * 按 (type, seasonCode) 统计各段位人数。用于排名工作台的段位分布条；
     * 归档赛季也走此查询（基于 ranking 快照行，非 user 实时段位）。
     * 仅返回有行的段位，前端对缺失段位补 0。
     */
    @Select("SELECT tier, COUNT(*) AS cnt FROM ranking " +
            "WHERE type = #{type} AND season = #{seasonCode} GROUP BY tier")
    List<Map<String, Object>> countByTier(@Param("type") String type,
                                          @Param("seasonCode") String seasonCode);
}
