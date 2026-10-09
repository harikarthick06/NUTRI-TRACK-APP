package com.nutritrack.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,128}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-128 characters and contain a letter and a number";

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password,
            @NotBlank @Size(max = 100) String name) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
    }

    public record UserSummary(long id, String email, String name, boolean onboardingCompleted) {
    }

    /** The refresh token itself travels only in an HttpOnly cookie, never in a response body. */
    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserSummary user) {
    }
}
