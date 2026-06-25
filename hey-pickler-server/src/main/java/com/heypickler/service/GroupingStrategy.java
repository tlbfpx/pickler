package com.heypickler.service;

import com.heypickler.dto.Participant;
import com.heypickler.entity.GroupAssignment;

import java.util.List;

/**
 * Distributes ranked participants across {@code groupCount} groups.
 *
 * <p>The input {@code ranked} list is pre-sorted by rank score (strongest first)
 * by the caller. Implementations return one {@link GroupAssignment} per
 * participant with these fields populated:
 * <ul>
 *   <li>{@code groupId} — the group <b>index</b> (0..groupCount-1); the grouping
 *       service resolves it to the persisted {@code match_group.id}.</li>
 *   <li>{@code userId} / {@code teamId} — copied from the participant (mutually
 *       exclusive).</li>
 *   <li>{@code seed} — 1-based assignment order.</li>
 * </ul>
 *
 * <p>MANUAL grouping does not use this interface — the admin assigns manually,
 * so only RANDOM / SERPENTINE have implementations.
 */
public interface GroupingStrategy {

    List<GroupAssignment> assign(List<Participant> ranked, int groupCount);
}
