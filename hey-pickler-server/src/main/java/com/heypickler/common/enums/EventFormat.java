package com.heypickler.common.enums;

import lombok.Getter;

@Getter
public enum EventFormat {
    SINGLES("单打"),
    DOUBLES("双打"),
    MIXED("混打");

    private final String label;

    EventFormat(String label) {
        this.label = label;
    }
}
