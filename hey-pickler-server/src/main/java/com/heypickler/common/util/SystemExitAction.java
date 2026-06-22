package com.heypickler.common.util;

import org.springframework.stereotype.Component;

/**
 * Default {@link ExitAction} that delegates to {@link System#exit(int)}.
 * Used by fail-fast startup components ({@code AdminBootstrapper},
 * {@code ProfileGuard}) to refuse start-up on known-insecure configurations.
 */
@Component
public class SystemExitAction implements ExitAction {
    @Override
    public void exit(int code) {
        System.exit(code);
    }
}
