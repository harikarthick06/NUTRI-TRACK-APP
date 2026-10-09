package com.nutritrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutritrack.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef!!".getBytes());

    private static JwtService service(String secret, Clock clock) {
        var props = new AppProperties(new AppProperties.Jwt(secret, 30, 14), null, null, null);
        return new JwtService(props, clock);
    }

    @Test
    void roundTripsUserIdEmailAndVersion() {
        JwtService jwt = service(SECRET, Clock.systemUTC());
        String token = jwt.createAccessToken(42, "a@b.com", 3);
        var claims = jwt.parse(token).orElseThrow();
        assertThat(claims.userId()).isEqualTo(42);
        assertThat(claims.email()).isEqualTo("a@b.com");
        assertThat(claims.tokenVersion()).isEqualTo(3);
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwt = service(SECRET, Clock.systemUTC());
        String token = jwt.createAccessToken(42, "a@b.com", 0);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(jwt.parse(tampered)).isEmpty();
        assertThat(jwt.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String other = Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffffffff".getBytes());
        String token = service(other, Clock.systemUTC()).createAccessToken(1, "x@y.com", 0);
        assertThat(service(SECRET, Clock.systemUTC()).parse(token)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        Instant issued = Instant.parse("2026-01-01T00:00:00Z");
        String token = service(SECRET, Clock.fixed(issued, ZoneOffset.UTC)).createAccessToken(1, "x@y.com", 0);
        JwtService later = service(SECRET, Clock.fixed(issued.plusSeconds(31 * 60), ZoneOffset.UTC));
        assertThat(later.parse(token)).isEmpty();
    }

    @Test
    void refusesMissingOrWeakSecret() {
        assertThatThrownBy(() -> service("", Clock.systemUTC())).hasMessageContaining("JWT_SECRET is not set");
        String weak = Base64.getEncoder().encodeToString("short".getBytes());
        assertThatThrownBy(() -> service(weak, Clock.systemUTC())).hasMessageContaining("at least 32 bytes");
    }
}
