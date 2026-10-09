package com.nutritrack.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthIT extends IntegrationTestBase {

    @Test
    void registerSetsAnHttpOnlyRefreshCookieAndNeverReturnsThePassword() throws Exception {
        User user = register();
        String setCookie = user.registration().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("nt_refresh=").contains("HttpOnly").contains("Path=/api/auth");
        String body = user.registration().getResponse().getContentAsString();
        assertThat(body).doesNotContain(user.password()).doesNotContainIgnoringCase("passwordHash");
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        User user = register();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.email(), "password", "another-pass-1", "name", "Dup"))))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordGetsAGenericMessage() throws Exception {
        User user = register();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.email(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));
        // Unknown emails get the same answer, so the endpoint doesn't reveal who has an account.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "nobody-" + user.email(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        User user = register();
        mvc.perform(as(user, get("/api/profile"))).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(user.email()));
    }

    @Test
    void refreshRotatesTheCookieAndLogoutEndsTheSession() throws Exception {
        User user = register();
        Cookie first = user.registration().getResponse().getCookie("nt_refresh");
        assertThat(first).isNotNull();

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie("nt_refresh");
        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mvc.perform(post("/api/auth/logout").cookie(second)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void changingThePasswordNeedsTheCurrentOne() throws Exception {
        User user = register();
        mvc.perform(withJson(user, put("/api/account/password"), Map.of("currentPassword", "nope-nope-1", "newPassword", "brand-new-pass-1")))
                .andExpect(status().is4xxClientError());
        mvc.perform(withJson(user, put("/api/account/password"), Map.of("currentPassword", user.password(), "newPassword", "brand-new-pass-1")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.email(), "password", "brand-new-pass-1"))))
                .andExpect(status().isOk());
    }

    @Test
    void deletingTheAccountRemovesItsData() throws Exception {
        User user = register();
        long food = customFood(user, "Delete-me dosa", Map.of("ENERGY_KCAL", 160));
        logGrams(user, food, "2026-10-01", "BREAKFAST", 100);
        mvc.perform(withJson(user, delete("/api/account"), Map.of("password", user.password()))).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.email(), "password", user.password()))))
                .andExpect(status().isUnauthorized());
    }
}
