package com.nutritrack.food;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnergyValidatorTest {
    private final EnergyValidator validator = new EnergyValidator();

    private static Map<String, BigDecimal> values(String... kv) {
        var m = new java.util.HashMap<String, BigDecimal>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], new BigDecimal(kv[i + 1]));
        }
        return m;
    }

    @Test
    void cooked_rice_from_sr28_is_consistent() {
        // USDA SR28 NDB 20445: 130 kcal, 2.69 g protein, 0.28 g fat, 28.17 g carbohydrate, 0.4 g fibre
        var check = validator.check(values("ENERGY_KCAL", "130", "PROTEIN", "2.69", "FAT_TOTAL", "0.28",
                "CARBOHYDRATE", "28.17", "FIBER", "0.4"));
        assertThat(check.status()).isEqualTo("CONSISTENT");
        assertThat(check.estimatedKcal()).isEqualByComparingTo("125.2");
    }

    @Test
    void flags_large_difference_but_keeps_reported_value() {
        var check = validator.check(values("ENERGY_KCAL", "50", "PROTEIN", "10", "FAT_TOTAL", "10",
                "AVAILABLE_CARBOHYDRATE", "10"));
        assertThat(check.status()).isEqualTo("DIFFERS");
        assertThat(check.reportedKcal()).isEqualByComparingTo("50");
        assertThat(check.estimatedKcal()).isEqualByComparingTo("170");
    }

    @Test
    void needs_all_macronutrients() {
        var check = validator.check(values("ENERGY_KCAL", "100", "PROTEIN", "5"));
        assertThat(check.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(check.estimatedKcal()).isNull();
    }
}
