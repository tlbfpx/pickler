package com.heypickler.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Loop-v2 D9 — schema-based auth mark for GET endpoints under /api/app/*.
 *
 * <p>Apply to controller method to declare its auth requirement explicitly.
 * The runtime {@code AppAuthFilter} uses the linked annotation when deciding
 * whether to bypass JWT validation, replacing the previous hard-coded
 * {@code PUBLIC_GET_PREFIXES} + {@code endsWith("/my-team")} check. The new
 * scheme has two benefits:
 *
 * <ul>
 *   <li>Adding a new user-scoped GET no longer requires editing
 *       {@code AppAuthFilter.shouldNotFilter}; just annotate.</li>
 *   <li>Auditability — anyone reading the controller can see the auth contract.</li>
 * </ul>
 *
 * <p>Pairs with {@link PublicAnonymousAccess}: a path may be annotated with
 * one but not both. They are mutually exclusive by design (a 4xx annotation
 * mismatch is caught at startup by {@code AppAuthFilterValidator}).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAppUser {
}
