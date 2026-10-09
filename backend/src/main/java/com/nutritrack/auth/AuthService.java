package com.nutritrack.auth;

import com.nutritrack.auth.AuthDtos.AuthResponse;
import com.nutritrack.auth.AuthDtos.LoginRequest;
import com.nutritrack.auth.AuthDtos.RegisterRequest;
import com.nutritrack.auth.AuthDtos.UserSummary;
import com.nutritrack.common.ApiException;
import com.nutritrack.config.AppProperties;
import com.nutritrack.security.JwtService;
import com.nutritrack.user.User;
import com.nutritrack.user.UserProfile;
import com.nutritrack.user.UserProfileRepository;
import com.nutritrack.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttempts;
    private final Duration refreshTtl;
    private final Clock clock;
    // Checked against unknown emails so response time does not reveal which accounts exist.
    private final String dummyHash;

    public AuthService(UserRepository users, UserProfileRepository profiles, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService, LoginAttemptService loginAttempts,
                       AppProperties props, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttempts = loginAttempts;
        this.refreshTtl = Duration.ofDays(props.jwt().refreshTtlDays());
        this.clock = clock;
        byte[] seed = new byte[16];
        RANDOM.nextBytes(seed);
        this.dummyHash = passwordEncoder.encode(HexFormat.of().formatHex(seed));
    }

    /** Result of a successful authentication: the response body plus the raw refresh token for the cookie. */
    public record Session(AuthResponse response, String refreshToken, Duration refreshTtl) {
    }

    @Transactional
    public Session register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = users.save(new User(email, passwordEncoder.encode(req.password())));
        UserProfile profile = profiles.save(new UserProfile(user.getId(), req.name().trim()));
        user.setLastLoginAt(clock.instant());
        return issue(user, profile);
    }

    @Transactional
    public Session login(LoginRequest req) {
        String email = normalize(req.email());
        if (loginAttempts.isBlocked(email)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed attempts. Please wait 15 minutes and try again.");
        }
        User user = users.findByEmail(email).orElse(null);
        boolean ok;
        if (user != null) {
            ok = passwordEncoder.matches(req.password(), user.getPasswordHash());
        } else {
            passwordEncoder.matches(req.password(), dummyHash);
            ok = false;
        }
        if (!ok) {
            loginAttempts.recordFailure(email);
            throw ApiException.unauthorized("Email or password is incorrect");
        }
        loginAttempts.recordSuccess(email);
        user.setLastLoginAt(clock.instant());
        UserProfile profile = profiles.findById(user.getId()).orElseThrow(() -> ApiException.notFound("Profile"));
        return issue(user, profile);
    }

    /** Rotates the refresh token: the presented token is revoked and a new one is issued. */
    @Transactional
    public Session refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("Session expired. Please log in again.");
        }
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Session expired. Please log in again."));
        if (!token.isActive(now)) {
            throw ApiException.unauthorized("Session expired. Please log in again.");
        }
        token.revoke(now);
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Session expired. Please log in again."));
        UserProfile profile = profiles.findById(user.getId()).orElseThrow(() -> ApiException.notFound("Profile"));
        return issue(user, profile);
    }

    /**
     * Logs the user out everywhere: revokes all refresh tokens and invalidates outstanding access tokens.
     * Unknown or missing tokens are ignored so logout is always safe to call.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            refreshTokens.revokeAllForUser(token.getUserId(), clock.instant());
            users.findById(token.getUserId()).ifPresent(User::bumpTokenVersion);
        });
    }

    /** Invalidates every session for a user and issues a fresh one (after a password change). */
    @Transactional
    public Session restartSessions(User user) {
        refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        user.bumpTokenVersion();
        users.saveAndFlush(user);
        UserProfile profile = profiles.findById(user.getId()).orElseThrow(() -> ApiException.notFound("Profile"));
        return issue(user, profile);
    }

    private Session issue(User user, UserProfile profile) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        refreshTokens.save(new RefreshToken(user.getId(), hash(raw), now.plus(refreshTtl), now));
        String access = jwtService.createAccessToken(user.getId(), user.getEmail(), user.getTokenVersion());
        var summary = new UserSummary(user.getId(), user.getEmail(), profile.getName(), profile.isOnboardingCompleted());
        return new Session(new AuthResponse(access, "Bearer", jwtService.accessTtlSeconds(), summary), raw, refreshTtl);
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
