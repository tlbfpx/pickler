package com.heypickler.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loop-v2 D10 — registration transition matrix sanity.
 * Event transitions are covered by direct EventServiceTest; this file owns
 * only the registration-side FSM because it has its own table now.
 */
class StatusTransitionValidatorTest {

    @Test
    void registration_registered_toCheckedIn_isAllowed() {
        assertTrue(StatusTransitionValidator.canRegistrationTransit("REGISTERED", "CHECKED_IN"));
    }

    @Test
    void registration_registered_toWithdrawn_isAllowed() {
        assertTrue(StatusTransitionValidator.canRegistrationTransit("REGISTERED", "WITHDRAWN"));
    }

    @Test
    void registration_checkedIn_toWithdrawn_isAllowed() {
        assertTrue(StatusTransitionValidator.canRegistrationTransit("CHECKED_IN", "WITHDRAWN"));
    }

    @Test
    void registration_checkedIn_toRegistered_isRejected() {
        // 一旦签到就不能再变回 REGISTERED
        assertFalse(StatusTransitionValidator.canRegistrationTransit("CHECKED_IN", "REGISTERED"));
    }

    @Test
    void registration_withdrawn_isTerminal() {
        assertFalse(StatusTransitionValidator.canRegistrationTransit("WITHDRAWN", "REGISTERED"));
        assertFalse(StatusTransitionValidator.canRegistrationTransit("WITHDRAWN", "CHECKED_IN"));
        assertFalse(StatusTransitionValidator.canRegistrationTransit("WITHDRAWN", "WITHDRAWN"));
    }

    @Test
    void registration_unknownStatus_isRejected() {
        // 任何 unknown source/target 都会被拒，防止 if/else 漏掉新值
        assertFalse(StatusTransitionValidator.canRegistrationTransit("REGISTERED", "PAUSED"));
        assertFalse(StatusTransitionValidator.canRegistrationTransit("PAUSED", "WITHDRAWN"));
    }

    @Test
    void registration_nullRejected() {
        assertFalse(StatusTransitionValidator.canRegistrationTransit(null, "REGISTERED"));
        assertFalse(StatusTransitionValidator.canRegistrationTransit("REGISTERED", null));
    }

    @Test
    void getAllowedRegistrationTargets_returnsExpected() {
        assertTrue(StatusTransitionValidator.getAllowedRegistrationTargets("REGISTERED")
                .contains("CHECKED_IN"));
        assertTrue(StatusTransitionValidator.getAllowedRegistrationTargets("REGISTERED")
                .contains("WITHDRAWN"));
        assertTrue(StatusTransitionValidator.getAllowedRegistrationTargets("CHECKED_IN")
                .contains("WITHDRAWN"));
    }
}
