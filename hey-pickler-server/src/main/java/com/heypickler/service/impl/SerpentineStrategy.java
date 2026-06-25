package com.heypickler.service.impl;

import com.heypickler.dto.Participant;
import com.heypickler.entity.GroupAssignment;
import com.heypickler.service.GroupingStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Serpentine (snake) distribution: walk the rank-descending participants and
 * fold them into groups so each group gets a balanced slice of strong and weak
 * members. Within each round of {@code groupCount} participants the direction
 * reverses — round 0 fills groups 0..N-1, round 1 fills N-1..0, etc.
 *
 * <p>Result for 8 into 4: group0 = rank1 + rank8, group1 = rank2 + rank7, ...
 * Strong players are maximally spread across groups.
 */
public class SerpentineStrategy implements GroupingStrategy {

    @Override
    public List<GroupAssignment> assign(List<Participant> ranked, int groupCount) {
        if (groupCount <= 0) {
            throw new IllegalArgumentException("groupCount must be positive");
        }
        List<GroupAssignment> out = new ArrayList<>(ranked.size());
        int seed = 1;
        for (int i = 0; i < ranked.size(); i++) {
            int round = i / groupCount;
            int posInRound = i % groupCount;
            int groupIndex = (round % 2 == 0) ? posInRound : (groupCount - 1 - posInRound);
            out.add(toAssignment(ranked.get(i), groupIndex, seed++));
        }
        return out;
    }

    private GroupAssignment toAssignment(Participant p, int groupIndex, int seed) {
        GroupAssignment ga = new GroupAssignment();
        ga.setGroupId((long) groupIndex); // index; GroupingService resolves to match_group.id
        ga.setUserId(p.getUserId());
        ga.setTeamId(p.getTeamId());
        ga.setSeed(seed);
        return ga;
    }
}
