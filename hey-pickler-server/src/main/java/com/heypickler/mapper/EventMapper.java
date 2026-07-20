package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Event;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EventMapper extends BaseMapper<Event> {

    /**
     * 悲观锁：事务内锁住 event 行，串行化并发 complete()，防双发分（review #4 P2）。
     * 必须在 @Transactional 内调用，行锁随事务提交/回滚释放。
     */
    @Select("SELECT * FROM event WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Event selectForUpdate(Long id);
}
