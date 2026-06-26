package com.heypickler.vo;

import com.heypickler.common.enums.EventStatus;
import com.heypickler.common.enums.RegistrationStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyEventVO {
    private Long id;
    private String title;
    private String type;
    private String bannerUrl;
    private LocalDateTime eventTime;
    private String location;
    private EventStatus status;
    private RegistrationStatus registrationStatus;
    /**
     * Total PLACEMENT points earned from this event (sum across all
     * point_record rows with source='PLACEMENT' for this user+event).
     * Null when the event is not COMPLETED or the user earned nothing.
     */
    private Integer earnedPoints;
}
