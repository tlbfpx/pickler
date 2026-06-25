package com.heypickler.service.impl;

import com.heypickler.dto.Participant;
import com.heypickler.entity.GroupAssignment;
import com.heypickler.service.GroupingStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Random distribution: shuffle the ranked participants, then deal them out
 * round-robin so every group ends up within one member of every other
 * (ceil(count/groupCount) vs floor). {@code seed} is the post-shuffle deal
 * order, which is stable for a given shuffle.
 */
public class RandomStrategy implements GroupingStrategy {

    @Override
    public List<GroupAssignment> assign(List<Participant> ranked, int groupCount) {
        if (groupCount <= 0) {
            throw new IllegalArgumentException("groupCount must be positive");
        }
        List<Participant> shuffled = new ArrayList<>(ranked);
        Collections.shuffle(shuffled);

        List<GroupAssignment> out = new ArrayList<>(shuffled.size());
        int seed = 1;
        for (int i = 0; i < shuffled.size(); i++) {
            int groupIndex = i % groupCount;
            out.add(toAssignment(shuffled.get(i), groupIndex, seed++));
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
