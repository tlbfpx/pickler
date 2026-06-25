package com.heypickler.common.enums;

import lombok.Getter;

@Getter
public enum MatchStatus {
    SCHEDULED("待开打"),
    IN_PROGRESS("进行中"),
    COMPLETED("已结束");

    private final String label;

    MatchStatus(String label) {
        this.label = label;
    }
}