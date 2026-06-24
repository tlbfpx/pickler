package com.heypickler.vo;

import lombok.Data;

@Data
public class TeamVO {
    private Long id;
    private Long eventId;
    private Long member1UserId;
    private Long member2UserId;
    private String member1Name;
    private String member2Name;
    private String name;
    private String status;
}
