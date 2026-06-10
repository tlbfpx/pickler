package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    @JsonIgnore
    private String openid;
    @JsonIgnore
    private String unionId;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String city;
    private Integer starPoints;
    private Integer partyPoints;
    private String starTier;
    private String partyTier;
    private String status;
    private LocalDateTime lastLoginAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
