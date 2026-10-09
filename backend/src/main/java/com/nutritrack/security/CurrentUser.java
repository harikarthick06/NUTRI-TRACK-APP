package com.nutritrack.security;

import com.nutritrack.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the authenticated user's id. Every private query must be scoped with this id. */
public final class CurrentUser {
    private CurrentUser() {
    }

    public static long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        throw ApiException.unauthorized("Authentication required");
    }
}
