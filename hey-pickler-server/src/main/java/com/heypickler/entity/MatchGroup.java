package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("match_group")
public class MatchGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private Integer groupIndex;
    private String name;
}
