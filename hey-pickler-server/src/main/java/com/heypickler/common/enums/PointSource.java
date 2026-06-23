package com.heypickler.common.enums;

import lombok.Getter;

@Getter
public enum PointSource {
    REGISTRATION("报名"),
    CHECK_IN("签到"),
    PLACEMENT("名次"),
    MANUAL("管理员手动"),
    REDEEM("商城兑换"),
    ADJUST("系统纠错");

    private final String label;

    PointSource(String label) {
        this.label = label;
    }
}
