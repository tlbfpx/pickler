package com.heypickler.dto.app;

import lombok.Data;

@Data
public class EventListQuery {
    private String type;
    private String status;
    private int page = 1;
    private int size = 20;
}
