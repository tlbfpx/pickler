package com.heypickler.vo;

import lombok.Data;

/** R5 compare 响应。deltaPct 在 previous=0 时为 null（避免除零与 Infinity）；参见 spec R5。 */
@Data
public class CompareResultVO {
    private String metric;         // echo: users | registrations | revenue | events
    private double current;
    private double previous;
    private double deltaAbs;
    /** 0..1；当前-前/前。previous=0 时 null。 */
    private Double deltaPct;
}
