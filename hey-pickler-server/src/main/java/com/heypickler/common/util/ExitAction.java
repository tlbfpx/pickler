package com.heypickler.common.util;

/**
 * Functional seam for {@code System.exit}, enabling unit tests to verify
 * fail-fast paths without terminating the JVM. Production wiring uses
 * {@link SystemExitAction}; tests inject a mock that records the requested
 * exit code.
 */
@FunctionalInterface
public interface ExitAction {
    void exit(int code);
}
