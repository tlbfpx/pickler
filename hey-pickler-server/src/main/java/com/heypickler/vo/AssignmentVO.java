package com.heypickler.vo;

import lombok.Data;

@Data
public class AssignmentVO {
    private Long id;
    private Long userId;
    private Long teamId;
    /** Singles: the user's nickname. Teams: "member1 / member2". */
    private String displayName;
    private Integer seed;
}
