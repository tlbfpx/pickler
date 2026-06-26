package com.heypickler.service;

import com.heypickler.common.enums.GroupingStrategyType;
import com.heypickler.vo.GroupVO;

import java.util.List;

/**
 * Event grouping lifecycle: execute a strategy, preview, fine-tune, lock, unlock.
 *
 * <p>Locking ({@code event.groupingLocked=true}) freezes the roster — once locked,
 * re-grouping, reassignment, registration and cancellation are all rejected.
 */
public interface GroupingService {

    /** Execute a grouping strategy, replacing any existing (unlocked) groups. Returns the preview. */
    List<GroupVO> group(Long eventId, GroupingStrategyType strategy, int groupCount);

    /** Read the current groups with member names resolved. */
    List<GroupVO> getGroups(Long eventId);

    /** Move one assignment to another group (unlocked events only). */
    void reassign(Long eventId, Long assignmentId, Long targetGroupId);

    /** Freeze the current grouping. */
    void lock(Long eventId);

    /** Clear all groups/assignments and reopen the roster. */
    void unlock(Long eventId);
}
