package com.nutritrack.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AggregationIT extends IntegrationTestBase {

    private JsonNode nutrient(JsonNode daily, String code) {
        for (JsonNode n : daily.get("nutrients")) {
            if (n.get("code").asText().equals(code)) return n;
        }
        throw new AssertionError("no " + code);
    }

    @Test
    void dailyTotalsScaleByQuantityAndKeepMissingValuesUnknown() throws Exception {
        User user = register();
        long dal = customFood(user, "IT dal", Map.of("ENERGY_KCAL", 200, "PROTEIN", 10, "FAT_TOTAL", 8, "VITAMIN_C", 30));
        logGrams(user, dal, "2026-09-01", "BREAKFAST", 150);
        logGrams(user, dal, "2026-09-01", "LUNCH", 50);

        JsonNode daily = read(mvc.perform(as(user, get("/api/nutrition/daily").param("date", "2026-09-01"))).andReturn());
        assertThat(daily.get("entryCount").asInt()).isEqualTo(2);
        assertThat(nutrient(daily, "ENERGY_KCAL").get("amount").asDouble()).isEqualTo(400.0);
        assertThat(nutrient(daily, "PROTEIN").get("amount").asDouble()).isEqualTo(20.0);
        assertThat(nutrient(daily, "VITAMIN_C").get("amount").asDouble()).isEqualTo(60.0);
        assertThat(nutrient(daily, "VITAMIN_C").get("coverage").asText()).isEqualTo("COMPLETE");

        // No food had iron: unknown, not zero, and no shortfall is claimed.
        JsonNode iron = nutrient(daily, "IRON");
        assertThat(iron.has("amount")).isFalse();
        assertThat(iron.get("coverage").asText()).isEqualTo("NO_DATA");
        // Iron's reference differs by sex, which this new user hasn't set, so there may be no target at all.
        assertThat(iron.get("status").asText()).isIn("UNKNOWN", "NO_TARGET");
    }

    @Test
    void aFoodWithoutAValueMakesTheTotalPartial() throws Exception {
        User user = register();
        long withC = customFood(user, "IT guava", Map.of("ENERGY_KCAL", 68, "VITAMIN_C", 228));
        long withoutC = customFood(user, "IT rice", Map.of("ENERGY_KCAL", 130));
        logGrams(user, withC, "2026-09-02", "MORNING_SNACK", 100);
        logGrams(user, withoutC, "2026-09-02", "LUNCH", 200);

        JsonNode vitC = nutrient(read(mvc.perform(as(user, get("/api/nutrition/daily").param("date", "2026-09-02"))).andReturn()), "VITAMIN_C");
        assertThat(vitC.get("amount").asDouble()).isEqualTo(228.0);
        assertThat(vitC.get("coverage").asText()).isEqualTo("PARTIAL");
        assertThat(vitC.get("entriesWithData").asInt()).isEqualTo(1);
        assertThat(vitC.get("entries").asInt()).isEqualTo(2);
        assertThat(vitC.get("status").asText()).isNotEqualTo("BELOW_TARGET").isNotEqualTo("UNKNOWN");
    }

    @Test
    void diaryMealTotalsAddUp() throws Exception {
        User user = register();
        long food = customFood(user, "IT upma", Map.of("ENERGY_KCAL", 150, "PROTEIN", 4, "CARBOHYDRATE", 25, "FAT_TOTAL", 4, "FIBER", 2));
        logGrams(user, food, "2026-09-03", "BREAKFAST", 200);
        logGrams(user, food, "2026-09-03", "DINNER", 100);
        JsonNode day = read(mvc.perform(as(user, get("/api/diary").param("date", "2026-09-03"))).andReturn());
        assertThat(day.get("totals").get("energyKcal").get("amount").asDouble()).isEqualTo(450.0);
        for (JsonNode meal : day.get("meals")) {
            if (meal.get("mealType").asText().equals("BREAKFAST")) {
                assertThat(meal.get("totals").get("energyKcal").get("amount").asDouble()).isEqualTo(300.0);
            }
        }
    }

    @Test
    void weeklyAveragesCountOnlyLoggedDays() throws Exception {
        User user = register();
        long food = customFood(user, "IT pongal", Map.of("ENERGY_KCAL", 100));
        // Week of Monday 2026-09-07: two logged days, 1000 and 2000 kcal.
        logGrams(user, food, "2026-09-07", "LUNCH", 1000);
        logGrams(user, food, "2026-09-09", "LUNCH", 2000);

        JsonNode week = read(mvc.perform(as(user, get("/api/nutrition/weekly").param("date", "2026-09-09"))).andReturn());
        assertThat(week.get("from").asText()).isEqualTo("2026-09-07");
        assertThat(week.get("to").asText()).isEqualTo("2026-09-13");
        assertThat(week.get("loggedDays").asInt()).isEqualTo(2);
        JsonNode energy = null;
        for (JsonNode n : week.get("nutrients")) {
            if (n.get("code").asText().equals("ENERGY_KCAL")) energy = n;
        }
        assertThat(energy).isNotNull();
        assertThat(energy.toString()).contains("1500");
    }
}
