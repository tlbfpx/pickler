package com.heypickler.service;

import com.heypickler.dto.Participant;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure generator: N participants → N*(N-1)/2 round-robin matches.
 *
 * <p>Participants are passed as a flat list per group. Each match holds two
 * slots — one participant per slot. The output match has {@code groupId} set
 * to the supplied groupId and {@code status} unset (caller initializes it).
 * Slot columns are filled by the caller after generation.
 *
 * <p>Edge cases:
 * <ul>
 *   <li>N = 0 or 1 → 0 matches (nothing to pair).</li>
 *   <li>N = 2 → exactly 1 match.</li>
 * </ul>
 */
public class RoundRobinGenerator {

    /**
     * Build N*(N-1)/2 placeholder matches for a single group. The returned
     * matches have only {@code groupId} populated — callers fill in slot
     * columns (and eventId) before insert.
     */
    public List<com.heypickler.entity.Match> generate(List<Participant> participants, Long groupId) {
        int n = participants.size();
        if (n < 2) {
            return List.of();
        }
        // For an odd number of participants we treat the list as a circle
        // and pin one slot to a "bye" null for the fixture against position
        // (n - 1). For simplicity in this MVP (round-robin for groups whose
        // size is determined by GroupingService), we require N >= 2 and
        // generate pairs via the standard circle rotation.
        List<Participant> rotated = new ArrayList<>(participants);
        // If odd, pad with null so the algorithm still produces N rounds
        // (with a "bye" placeholder for the dummy slot).
        if (n % 2 == 1) {
            rotated.add(null);
            n++;
        }
        int rounds = n - 1;
        List<com.heypickler.entity.Match> out = new ArrayList<>();
        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < n / 2; i++) {
                Participant a = rotated.get(i);
                Participant b = rotated.get(n - 1 - i);
                // Skip byes: if either side is null, skip this match.
                if (a == null || b == null) {
                    continue;
                }
                out.add(newPlaceholder(groupId, a, b));
            }
            // Rotate: keep index 0, rotate the rest.
            if (rotated.size() > 2) {
                Participant last = rotated.remove(rotated.size() - 1);
                rotated.add(1, last);
            }
        }
        return out;
    }

    private com.heypickler.entity.Match newPlaceholder(Long groupId, Participant a, Participant b) {
        com.heypickler.entity.Match m = new com.heypickler.entity.Match();
        m.setGroupId(groupId);
        m.setSlotAUserId(a.getUserId());
        m.setSlotATeamId(a.getTeamId());
        m.setSlotBUserId(b.getUserId());
        m.setSlotBTeamId(b.getTeamId());
        return m;
    }
}