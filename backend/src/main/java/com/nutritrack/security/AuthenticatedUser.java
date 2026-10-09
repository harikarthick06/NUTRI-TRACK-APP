package com.nutritrack.security;

/** Principal stored in the security context for an authenticated request. */
public record AuthenticatedUser(long id, String email) {
}
