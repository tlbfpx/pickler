package com.heypickler.service;

import com.heypickler.dto.Participant;
import com.heypickler.entity.Match;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinGeneratorTest {

    private final RoundRobinGenerator gen = new RoundRobinGenerator();

    private List<Participant> users(int n) {
        List<Participant> ps = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            ps.add(Participant.singles((long) i, 100 - i));
        }
        return ps;
    }

    @Test
    void fourParticipants_yieldSixMatches() {
        List<Match> m = gen.generate(users(4), 10L);
        assertEquals(6, m.size());
        m.forEach(x -> assertEquals(10L, x.getGroupId()));
    }

    @Test
    void threeTeams_yieldThreeMatches() {
        List<Participant> ps = List.of(
                Participant.team(100L, 90),
                Participant.team(101L, 80),
                Participant.team(102L, 70));
        List<Match> m = gen.generate(ps, 5L);
        assertEquals(3, m.size());
        m.forEach(x -> {
            assertNull(x.getSlotAUserId());
            assertNotNull(x.getSlotATeamId());
            assertNull(x.getSlotBUserId());
            assertNotNull(x.getSlotBTeamId());
        });
    }

    @Test
    void twoParticipants_yieldOneMatch() {
        assertEquals(1, gen.generate(users(2), 1L).size());
    }

    @Test
    void oneParticipant_yieldZeroMatches() {
        assertTrue(gen.generate(users(1), 1L).isEmpty());
    }

    @Test
    void zeroParticipants_yieldZeroMatches() {
        assertTrue(gen.generate(List.of(), 1L).isEmpty());
    }

    @Test
    void everyPairPlaysExactlyOnce_noDuplicates_noSelfPlay() {
        // 5 participants -> 10 matches; each unordered pair appears once.
        List<Participant> ps = users(5);
        List<Match> m = gen.generate(ps, 1L);
        assertEquals(10, m.size());
        Set<String> seen = new HashSet<>();
        for (Match x : m) {
            long a = x.getSlotAUserId();
            long b = x.getSlotBUserId();
            assertNotEquals(a, b, "no self-play");
            String key = a < b ? a + "_" + b : b + "_" + a;
            assertTrue(seen.add(key), "duplicate pair: " + key);
        }
    }

    @Test
    void allGroupAssignmentsAppearAcrossMatches() {
        List<Participant> ps = users(4);
        List<Match> m = gen.generate(ps, 1L);
        Set<Long> inMatches = new HashSet<>();
        m.forEach(x -> { inMatches.add(x.getSlotAUserId()); inMatches.add(x.getSlotBUserId()); });
        Set<Long> participants = new HashSet<>();
        ps.forEach(p -> participants.add(p.getUserId()));
        assertEquals(participants, inMatches);
    }
}