package com.heypickler.vo;

import lombok.Data;

import java.util.List;

@Data
public class MatchVO {
    private Long id;
    private Long eventId;
    private Long groupId;
    private Long slotAUserId;
    private Long slotATeamId;
    private Long slotBUserId;
    private Long slotBTeamId;
    /** SINGLES: nickname. DOUBLES/MIXED: "m1 / m2". */
    private String slotADisplayName;
    private String slotBDisplayName;
    private String status;            // SCHEDULED | IN_PROGRESS | COMPLETED
    private List<GameScore> games;
    private Integer gamesWonA;
    private Integer gamesWonB;
    private java.time.LocalDateTime submittedAt;
    private java.time.LocalDateTime completedAt;

    @Data
    public static class GameScore {
        private int game;
        private int a;
        private int b;
    }
}