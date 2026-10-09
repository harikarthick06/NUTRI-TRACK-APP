package com.nutritrack.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** One user must never see or change another user's data; every attempt looks like the record doesn't exist. */
class IsolationIT extends IntegrationTestBase {

    @Test
    void anotherUsersRecordsAreInvisible() throws Exception {
        User owner = register();
        User other = register();

        long food = customFood(owner, "Owner's secret sambar", Map.of("ENERGY_KCAL", 90, "PROTEIN", 4));
        long entry = logGrams(owner, food, "2026-10-02", "LUNCH", 200);
        long weight = read(mvc.perform(withJson(owner, post("/api/weight-logs"), Map.of("date", "2026-10-02", "weightKg", 70.5)))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        JsonNode waterDay = read(mvc.perform(withJson(owner, post("/api/water-logs"), Map.of("date", "2026-10-02", "amountMl", 300)))
                .andExpect(status().isCreated()).andReturn());
        long water = waterDay.get("entries").get(0).get("id").asLong();

        // Custom food
        mvc.perform(as(other, get("/api/foods/" + food))).andExpect(status().isNotFound());
        mvc.perform(withJson(other, put("/api/foods/" + food), Map.of("name", "Hijacked", "basisQuantity", 100, "basisUnit", "G", "nutrients", Map.of())))
                .andExpect(status().isNotFound());
        mvc.perform(as(other, delete("/api/foods/" + food))).andExpect(status().isNotFound());
        JsonNode search = read(mvc.perform(as(other, get("/api/foods/search").param("q", "secret sambar"))).andExpect(status().isOk()).andReturn());
        assertThat(search.get("items").findValuesAsText("name")).doesNotContain("Owner's secret sambar");
        // Logging someone else's custom food is refused too
        mvc.perform(withJson(other, post("/api/diary/entries"),
                        Map.of("foodId", food, "date", "2026-10-02", "mealType", "LUNCH", "quantity", 100, "unit", "G")))
                .andExpect(status().isNotFound());

        // Diary entry
        mvc.perform(withJson(other, put("/api/diary/entries/" + entry),
                        Map.of("foodId", food, "date", "2026-10-02", "mealType", "LUNCH", "quantity", 1, "unit", "G")))
                .andExpect(status().isNotFound());
        mvc.perform(as(other, delete("/api/diary/entries/" + entry))).andExpect(status().isNotFound());
        JsonNode otherDiary = read(mvc.perform(as(other, get("/api/diary").param("date", "2026-10-02"))).andExpect(status().isOk()).andReturn());
        assertThat(otherDiary.get("entryCount").asInt()).isZero();

        // Weight and water
        mvc.perform(withJson(other, put("/api/weight-logs/" + weight), Map.of("date", "2026-10-02", "weightKg", 99))).andExpect(status().isNotFound());
        mvc.perform(as(other, delete("/api/weight-logs/" + weight))).andExpect(status().isNotFound());
        mvc.perform(withJson(other, put("/api/water-logs/" + water), Map.of("date", "2026-10-02", "amountMl", 1))).andExpect(status().isNotFound());
        mvc.perform(as(other, delete("/api/water-logs/" + water))).andExpect(status().isNotFound());

        // Reports and exports only cover the caller's own data
        String csv = mvc.perform(as(other, get("/api/exports/diary.csv").param("from", "2026-10-01").param("to", "2026-10-03")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).doesNotContain("secret sambar");

        // The owner still has everything
        JsonNode ownerDiary = read(mvc.perform(as(owner, get("/api/diary").param("date", "2026-10-02"))).andExpect(status().isOk()).andReturn());
        assertThat(ownerDiary.get("entryCount").asInt()).isEqualTo(1);
    }
}
