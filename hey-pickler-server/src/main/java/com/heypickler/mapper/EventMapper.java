package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Event;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventMapper extends BaseMapper<Event> {
}
