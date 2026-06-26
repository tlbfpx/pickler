package com.heypickler.vo;

import lombok.Data;

import java.util.Map;

@Data
public class PlacementPointsVO {
    /** rank (1-based) -> points. */
    private Map<Integer, Integer> points;
    /** "default" if no per-event override; "custom" otherwise. */
    private String source;
}