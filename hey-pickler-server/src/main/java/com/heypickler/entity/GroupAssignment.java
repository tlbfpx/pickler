package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("group_assignment")
public class GroupAssignment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private Long eventId;
    private Long userId;
    private Long teamId;
    private Integer seed;
}
