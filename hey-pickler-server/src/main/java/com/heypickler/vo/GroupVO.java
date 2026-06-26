package com.heypickler.vo;

import lombok.Data;

import java.util.List;

@Data
public class GroupVO {
    private Long id;
    private Long eventId;
    private Integer groupIndex;
    private String name;
    private List<AssignmentVO> assignments;
}
