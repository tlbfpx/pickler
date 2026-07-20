package com.heypickler.vo;

import lombok.Data;

/** R3 top events 响应元素。 */
@Data
public class TopEventVO {
    private Long eventId;
    private String title;
    private double value;
    private String metric;          // echo: registrations | revenue | fillRate
    private Integer maxParticipants;
    private Integer currentParticipants;
}
