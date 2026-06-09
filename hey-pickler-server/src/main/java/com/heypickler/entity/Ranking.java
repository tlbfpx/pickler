package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "ranking", autoResultMap = true)
public class Ranking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String tier;
    @TableField("`rank`")
    private Integer rank;
    private Integer points;
    @TableField("`change`")
    private Integer change;
    private String season;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
