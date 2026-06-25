package com.heypickler.service;

import com.heypickler.dto.Participant;
import com.heypickler.entity.GroupAssignment;
import com.heypickler.service.impl.SerpentineStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SerpentineStrategyTest {

    private final SerpentineStrategy strategy = new SerpentineStrategy();

    /** Build N singles participants ranked by descending score: N*10, (N-1)*10, ... */
    private List<Participant> ranked(int n) {
        List<Participant> ps = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ps.add(Participant.singles((long) (i + 1), (n - i) * 10)); // ids 1..n, scores n*10..10
        }
        return ps;
    }

    @Test
    void serpentine_distributesStrongAcrossGroups() {
        // 8 participants, scores 80,70,60,50,40,30,20,10 (ids 1..8) into 4 groups.
        List<Participant> ps = ranked(8);
        List<GroupAssignment> result = strategy.assign(ps, 4);

        assertEquals(8, result.size());
        assertEquals(4, result.stream().map(GroupAssignment::getGroupId).distinct().count());

        // group index -> member ids
        Map<Long, List<Long>> byGroup = result.stream().collect(
                Collectors.groupingBy(GroupAssignment::getGroupId,
                        Collectors.mapping(GroupAssignment::getUserId, Collectors.toList())));

        // Strongest(1) and weakest(8) land together in group 0; 2 & 7 in group 1; etc.
        assertEquals(List.of(1L, 8L), sorted(byGroup.get(0L)));
        assertEquals(List.of(2L, 7L), sorted(byGroup.get(1L)));
        assertEquals(List.of(3L, 6L), sorted(byGroup.get(2L)));
        assertEquals(List.of(4L, 5L), sorted(byGroup.get(3L)));
    }

    @Test
    void serpentine_groupsAreBalanced_whenUneven() {
        // 9 into 4 groups -> sizes [3,2,2,2]
        List<GroupAssignment> result = strategy.assign(ranked(9), 4);
        Map<Long, Long> sizes = result.stream().collect(
                Collectors.groupingBy(GroupAssignment::getGroupId, Collectors.counting()));
        long maxSize = sizes.values().stream().mapToLong(v -> v).max().orElseThrow();
        long minSize = sizes.values().stream().mapToLong(v -> v).min().orElseThrow();
        assertTrue(maxSize - minSize <= 1, "groups must differ by at most 1");
    }

    @Test
    void serpentine_seedsAreConsecutiveFromOne() {
        List<GroupAssignment> result = strategy.assign(ranked(6), 3);
        List<Integer> seeds = result.stream().map(GroupAssignment::getSeed).sorted().collect(Collectors.toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), seeds);
    }

    @Test
    void serpentine_copiesUserIdAndLeavesTeamIdNull() {
        List<GroupAssignment> result = strategy.assign(ranked(2), 2);
        result.forEach(a -> {
            assertNotNull(a.getUserId());
            assertNull(a.getTeamId());
        });
    }

    @Test
    void serpentine_carriesTeamIdForTeamParticipants() {
        List<Participant> teams = List.of(
                Participant.team(11L, 90), Participant.team(12L, 80));
        List<GroupAssignment> result = strategy.assign(teams, 2);
        result.forEach(a -> {
            assertNull(a.getUserId());
            assertNotNull(a.getTeamId());
        });
    }

    @Test
    void serpentine_moreGroupsThanParticipants_assignsEachToOneGroup() {
        // 2 participants, 5 groups -> only groups 0 and 1 used, 1 member each.
        List<GroupAssignment> result = strategy.assign(ranked(2), 5);
        assertEquals(2, result.size());
        assertEquals(2, result.stream().map(GroupAssignment::getGroupId).distinct().count());
    }

    @Test
    void serpentine_emptyInput_returnsEmpty() {
        assertTrue(strategy.assign(Collections.emptyList(), 4).isEmpty());
    }

    @Test
    void serpentine_nonPositiveGroupCount_throws() {
        assertThrows(IllegalArgumentException.class, () -> strategy.assign(ranked(4), 0));
    }

    private List<Long> sorted(List<Long> in) {
        List<Long> out = new ArrayList<>(in);
        Collections.sort(out);
        return out;
    }
}
