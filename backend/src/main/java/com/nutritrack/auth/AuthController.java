package com.nutritrack.auth;

import com.nutritrack.auth.AuthDtos.AuthResponse;
import com.nutritrack.auth.AuthDtos.LoginRequest;
import com.nutritrack.auth.AuthDtos.RegisterRequest;
import com.nutritrack.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {
    public static final String REFRESH_COOKIE = "nt_refresh";
    private final AuthService authService;
    private final AppProperties props;

    public AuthController(AuthService authService, AppProperties props) {
        this.authService = authService;
        this.props = props;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account and start a session")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletResponse res) {
        return withCookie(authService.register(req), res);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletResponse res) {
        return withCookie(authService.login(req), res);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh cookie for a new access token (rotates the refresh token)")
    public AuthResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                HttpServletResponse res) {
        return withCookie(authService.refresh(refreshToken), res);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Log out on all devices and clear the refresh cookie")
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                       HttpServletResponse res) {
        authService.logout(refreshToken);
        res.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    AuthResponse withCookie(AuthService.Session session, HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), session.refreshTtl()).toString());
        return session.response();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.cookie().secure())
                .sameSite(props.cookie().sameSite())
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
