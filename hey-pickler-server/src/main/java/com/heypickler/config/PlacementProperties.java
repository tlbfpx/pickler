package com.heypickler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Per-event placement-points fallback (Spec 3). When an event has no row in
 * `event_placement_points`, {@code defaultPoints} is consulted.
 */
@Data
@Component
@ConfigurationProperties(prefix = "hey-pickler.placement")
public class PlacementProperties {

    /** rank (1-based) -> points for singles/doubles placement (split 50/50). */
    private Map<Integer, Integer> defaultPoints = Collections.emptyMap();

    public Map<Integer, Integer> getDefaultPoints() {
        return defaultPoints;
    }

    public void setDefaultPoints(Map<Integer, Integer> defaultPoints) {
        this.defaultPoints = defaultPoints == null ? Collections.emptyMap() : defaultPoints;
    }
}