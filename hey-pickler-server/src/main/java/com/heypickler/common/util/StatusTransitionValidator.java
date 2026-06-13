package com.heypickler.common.util;

import java.util.Map;
import java.util.Set;

public class StatusTransitionValidator {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "DRAFT", Set.of("OPEN", "CANCELLED"),
            "OPEN", Set.of("FULL", "IN_PROGRESS", "CANCELLED"),
            "FULL", Set.of("OPEN", "IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("COMPLETED", "CANCELLED"),
            "COMPLETED", Set.<String>of(),
            "CANCELLED", Set.<String>of()
    );

    public static boolean canTransit(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return false;
        }
        Set<String> targets = ALLOWED_TRANSITIONS.get(from);
        return targets != null && targets.contains(to);
    }

    public static Set<String> getAllowedTargets(String from) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
    }
}
