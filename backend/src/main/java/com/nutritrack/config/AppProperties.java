package com.nutritrack.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the nutritrack.* settings in application.yml (all sourced from environment variables). */
@ConfigurationProperties(prefix = "nutritrack")
public record AppProperties(Jwt jwt, Cors cors, Cookie cookie, Providers providers) {

    public record Jwt(String secret, long accessTtlMinutes, long refreshTtlDays) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Cookie(boolean secure, String sameSite) {
    }

    public record Providers(Usda usda, OpenFoodFacts openfoodfacts) {
    }

    public record Usda(boolean enabled, String apiKey, String baseUrl) {
    }

    public record OpenFoodFacts(boolean enabled, String baseUrl, String userAgent) {
    }
}
