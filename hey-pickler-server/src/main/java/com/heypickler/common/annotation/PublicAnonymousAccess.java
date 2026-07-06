package com.heypickler.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Loop-v2 D9 — marks a controller method as a public, anonymous endpoint
 * (no JWT required). Pairs with {@link RequireAppUser}; mutually exclusive.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicAnonymousAccess {
}
