package com.heypickler.dto.app;

import lombok.Data;

@Data
public class RankingQuery {
    private String type;
    private String tier;
    private int page = 1;
    private int size = 20;
}
