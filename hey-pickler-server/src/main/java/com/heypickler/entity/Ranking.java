package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ranking")
public class Ranking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String tier;
    private Integer rank;
    private Integer points;
    private Integer change;
    private String season;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
