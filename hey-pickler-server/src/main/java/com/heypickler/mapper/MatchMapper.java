package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Match;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MatchMapper extends BaseMapper<Match> {
}