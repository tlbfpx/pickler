package com.heypickler.vo;

import lombok.Data;

/** R4 attendance funnel 响应。 */
@Data
public class AttendanceFunnelVO {
    private String range;
    private long registered;
    private long checkedIn;
    /** 0..1；registered=0 时为 null（避免 NaN）。 */
    private Double noShowRate;
}
