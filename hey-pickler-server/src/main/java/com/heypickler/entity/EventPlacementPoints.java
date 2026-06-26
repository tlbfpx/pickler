package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName("event_placement_points")
public class EventPlacementPoints {

    @TableId(type = IdType.INPUT)
    private Long eventId;

    /** Raw JSON string in the `points` column. Use {@link #getPointsMap()} for typed access. */
    private String points;

    private LocalDateTime updatedAt;

    @JsonIgnore
    public Map<Integer, Integer> getPointsMap() {
        if (points == null || points.isEmpty()) {
            return Map.of();
        }
        try {
            return new ObjectMapper().readValue(points,
                    new TypeReference<Map<Integer, Integer>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    public void setPointsMap(Map<Integer, Integer> map) {
        if (map == null) {
            this.points = null;
            return;
        }
        try {
            this.points = new ObjectMapper().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            this.points = "{}";
        }
    }
}