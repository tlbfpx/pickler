package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heypickler.common.enums.MatchStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@TableName("match_record")
public class Match {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;
    private Long groupId;

    private Long slotAUserId;
    private Long slotATeamId;
    private Long slotBUserId;
    private Long slotBTeamId;

    /** Stored as enum name in DB; mapped via mybatis-plus default enum handler. */
    private MatchStatus status;

    /** JSON string in DB; use {@link #getGameList()} / {@link #setGameList(List)} to access. */
    private String games;

    private Integer gamesWonA;
    private Integer gamesWonB;

    private Long submittedByUserId;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @JsonIgnore
    public List<GameScore> getGameList() {
        if (games == null || games.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return new ObjectMapper().readValue(games,
                    new com.fasterxml.jackson.core.type.TypeReference<List<GameScore>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    public void setGameList(List<GameScore> games) {
        if (games == null) {
            this.games = null;
            return;
        }
        try {
            this.games = new ObjectMapper().writeValueAsString(games);
        } catch (JsonProcessingException e) {
            this.games = "[]";
        }
    }

    /** One game in the match (best of 3). */
    @Data
    public static class GameScore {
        private int game;
        private int a;
        private int b;
    }
}