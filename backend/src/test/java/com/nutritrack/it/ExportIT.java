package com.nutritrack.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class ExportIT extends IntegrationTestBase {

    private MockHttpServletResponse export(User user, String path, Map<String, String> params) throws Exception {
        var req = as(user, get(path));
        params.forEach(req::param);
        return mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse();
    }

    private String body(MockHttpServletResponse res) {
        return new String(res.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void diaryCsvIsADownloadWithBomAndNeutralisedFormulas() throws Exception {
        User user = register();
        long food = customFood(user, "=HYPERLINK(\"http://evil.example\",\"click\")", Map.of("ENERGY_KCAL", 120));
        logGrams(user, food, "2026-09-10", "DINNER", 100);

        MockHttpServletResponse res = export(user, "/api/exports/diary.csv", Map.of("from", "2026-09-10", "to", "2026-09-10"));
        assertThat(res.getContentType()).startsWith("text/csv");
        assertThat(res.getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment").contains(".csv");
        assertThat(res.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        String csv = body(res);
        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("'=HYPERLINK");
        assertThat(csv).doesNotContain(",=HYPERLINK").doesNotContain(",\"=HYPERLINK");
    }

    @Test
    void specAliasesServeTheSameExports() throws Exception {
        User user = register();
        long food = customFood(user, "IT kootu", Map.of("ENERGY_KCAL", 90, "PROTEIN", 3));
        logGrams(user, food, "2026-09-11", "LUNCH", 100);
        Map<String, String> range = Map.of("from", "2026-09-11", "to", "2026-09-11");
        assertThat(body(export(user, "/api/export/diary", range))).isEqualTo(body(export(user, "/api/exports/diary.csv", range)));
        assertThat(body(export(user, "/api/export/nutrition", range))).isEqualTo(body(export(user, "/api/exports/nutrient-summary.csv", range)));
    }

    @Test
    void weightAndDailyReportExportsWork() throws Exception {
        User user = register();
        mvc.perform(withJson(user, post("/api/weight-logs"), Map.of("date", "2026-09-12", "weightKg", 68.2))).andExpect(status().isCreated());
        assertThat(body(export(user, "/api/exports/weight.csv", Map.of("from", "2026-09-01", "to", "2026-09-30")))).contains("2026-09-12").contains("68.2");

        long food = customFood(user, "IT poriyal", Map.of("ENERGY_KCAL", 80));
        logGrams(user, food, "2026-09-12", "LUNCH", 100);
        String daily = body(export(user, "/api/exports/daily-report.csv", Map.of("date", "2026-09-12")));
        assertThat(daily).contains("2026-09-12,ENERGY_KCAL,Energy,KCAL,ENERGY,80,1,1,COMPLETE");
        // The food has no protein value: the cell is empty (unknown), not 0.
        assertThat(daily).contains("2026-09-12,PROTEIN,Protein,G,MACRONUTRIENT,,0,1,NO_DATA");
    }

    @Test
    void exportsRejectOverlongRanges() throws Exception {
        User user = register();
        mvc.perform(as(user, get("/api/exports/diary.csv").param("from", "2020-01-01").param("to", "2026-01-01")))
                .andExpect(status().isBadRequest());
    }
}
