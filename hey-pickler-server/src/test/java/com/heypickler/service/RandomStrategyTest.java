package com.heypickler.service;

import com.heypickler.dto.Participant;
import com.heypickler.entity.GroupAssignment;
import com.heypickler.service.impl.RandomStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RandomStrategyTest {

    private final RandomStrategy strategy = new RandomStrategy();

    private List<Participant> ranked(int n) {
        List<Participant> ps = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ps.add(Participant.singles((long) (i + 1), (n - i) * 10));
        }
        return ps;
    }

    @Test
    void random_assignsEveryParticipantExactlyOnce() {
        List<GroupAssignment> result = strategy.assign(ranked(10), 3);
        assertEquals(10, result.size());
        assertEquals(10, result.stream().map(GroupAssignment::getUserId).distinct().count());
    }

    @Test
    void random_groupSizesDifferByAtMostOne() {
        List<GroupAssignment> result = strategy.assign(ranked(10), 3);
        Map<Long, Long> sizes = result.stream().collect(
                Collectors.groupingBy(GroupAssignment::getGroupId, Collectors.counting()));
        long max = sizes.values().stream().mapToLong(v -> v).max().orElseThrow();
        long min = sizes.values().stream().mapToLong(v -> v).min().orElseThrow();
        assertTrue(max - min <= 1, "round-robin must balance groups within 1");
        assertEquals(3, sizes.size());
    }

    @Test
    void random_usesAllGroupIndices() {
        List<GroupAssignment> result = strategy.assign(ranked(8), 4);
        // group indices are 0..3
        for (int g = 0; g < 4; g++) {
            final long group = g;
            assertTrue(result.stream().anyMatch(a -> a.getGroupId() == group), "missing group " + group);
        }
    }

    @Test
    void random_seedsAreConsecutiveFromOne() {
        List<GroupAssignment> result = strategy.assign(ranked(7), 2);
        List<Integer> seeds = result.stream().map(GroupAssignment::getSeed).sorted().collect(Collectors.toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), seeds);
    }

    @Test
    void random_emptyInput_returnsEmpty() {
        assertTrue(strategy.assign(List.of(), 4).isEmpty());
    }

    @Test
    void random_nonPositiveGroupCount_throws() {
        assertThrows(IllegalArgumentException.class, () -> strategy.assign(ranked(4), 0));
    }
}
