package com.heypickler.common.util;

import java.util.Map;
import java.util.Set;

public class StatusTransitionValidator {

    /** Event lifecycle states — full FSM. */
    private static final Map<String, Set<String>> EVENT_TRANSITIONS = Map.of(
            "DRAFT", Set.of("OPEN", "CANCELLED"),
            "OPEN", Set.of("FULL", "IN_PROGRESS", "CANCELLED"),
            "FULL", Set.of("OPEN", "IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("COMPLETED", "CANCELLED"),
            "COMPLETED", Set.<String>of(),
            "CANCELLED", Set.<String>of()
    );

    /**
     * Loop-v2 D10 — registration lifecycle states.
     * Brings {@code EventServiceImpl.updateRegistrationStatus} under the same
     * state-machine contract that {@code updateEvent} already uses. Without
     * this map, future status values (e.g. PAUSED, REFUNDED) would either be
     * silently rejected by hardcoded if/else chains or — worse — silently
     * accepted because the new value doesn't match any string in the chain.
     */
    private static final Map<String, Set<String>> REGISTRATION_TRANSITIONS = Map.of(
            "REGISTERED", Set.of("CHECKED_IN", "WITHDRAWN"),
            "CHECKED_IN", Set.of("WITHDRAWN"),
            "WITHDRAWN", Set.<String>of()
    );

    public static boolean canTransit(String from, String to) {
        return canTransitIn(EVENT_TRANSITIONS, from, to);
    }

    /** Loop-v2 D10 — registration status state-machine gate. */
    public static boolean canRegistrationTransit(String from, String to) {
        return canTransitIn(REGISTRATION_TRANSITIONS, from, to);
    }

    public static Set<String> getAllowedTargets(String from) {
        return EVENT_TRANSITIONS.getOrDefault(from, Set.of());
    }

    public static Set<String> getAllowedRegistrationTargets(String from) {
        return REGISTRATION_TRANSITIONS.getOrDefault(from, Set.of());
    }

    private static boolean canTransitIn(Map<String, Set<String>> table, String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return false;
        }
        Set<String> targets = table.get(from);
        return targets != null && targets.contains(to);
    }
}
