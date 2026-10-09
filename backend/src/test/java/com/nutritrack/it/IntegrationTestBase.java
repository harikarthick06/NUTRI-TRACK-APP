package com.nutritrack.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Runs the whole application against a real MySQL database (default {@code nutritrack_test}).
 * Point it elsewhere with IT_DB_URL, IT_DB_USERNAME and IT_DB_PASSWORD. Every test registers fresh users,
 * so runs don't interfere with each other and need no cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("IT_DB_URL",
                "jdbc:mysql://localhost:3306/nutritrack_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"));
        registry.add("spring.datasource.username", () -> env("IT_DB_USERNAME", "nutritrack"));
        registry.add("spring.datasource.password", () -> env("IT_DB_PASSWORD", ""));
        // A throwaway signing key per run, so no secret lives in the repository.
        byte[] key = new byte[48];
        new SecureRandom().nextBytes(key);
        String secret = Base64.getEncoder().encodeToString(key);
        registry.add("nutritrack.jwt.secret", () -> secret);
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected record User(String email, String password, String token, MvcResult registration) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    protected User register() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-1";
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password, "name", "IT user"))))
                .andExpect(status().isCreated())
                .andReturn();
        return new User(email, password, read(res).get("accessToken").asText(), res);
    }

    protected JsonNode read(MvcResult res) throws Exception {
        return json.readTree(res.getResponse().getContentAsString());
    }

    protected MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, user.bearer());
    }

    protected MockHttpServletRequestBuilder withJson(User user, MockHttpServletRequestBuilder req, Object body) throws Exception {
        return as(user, req).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    /** A custom food with exactly these values per 100 g; nutrients left out stay unknown. */
    protected long customFood(User user, String name, Map<String, Number> per100g) throws Exception {
        var body = Map.of("name", name, "basisQuantity", 100, "basisUnit", "G", "nutrients", per100g);
        MvcResult res = mvc.perform(withJson(user, post("/api/foods/custom"), body))
                .andExpect(status().isCreated())
                .andReturn();
        return read(res).get("id").asLong();
    }

    protected long logGrams(User user, long foodId, String date, String meal, double grams) throws Exception {
        var body = Map.of("foodId", foodId, "date", date, "mealType", meal, "quantity", grams, "unit", "G");
        MvcResult res = mvc.perform(withJson(user, post("/api/diary/entries"), body))
                .andExpect(status().isCreated())
                .andReturn();
        return read(res).get("id").asLong();
    }
}
