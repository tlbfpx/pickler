package com.heypickler.common.enums;

import lombok.Getter;

@Getter
public enum GroupingStrategyType {
    RANDOM("随机"),
    SERPENTINE("蛇形按排名"),
    MANUAL("手动");

    private final String label;

    GroupingStrategyType(String label) {
        this.label = label;
    }
}
