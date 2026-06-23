package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("season")
public class Season {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;          // STAR | PARTY
    private String code;          // 2026-Q2
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;        // CURRENT | ARCHIVED
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
