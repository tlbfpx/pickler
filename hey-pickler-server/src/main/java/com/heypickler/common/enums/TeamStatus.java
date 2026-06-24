package com.heypickler.common.enums;

import lombok.Getter;

@Getter
public enum TeamStatus {
    PENDING("待确认"),
    CONFIRMED("已确认");

    private final String label;

    TeamStatus(String label) {
        this.label = label;
    }
}
