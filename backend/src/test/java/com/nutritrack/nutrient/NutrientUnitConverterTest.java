package com.nutritrack.nutrient;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NutrientUnitConverterTest {
    private final NutrientUnitConverter converter = new NutrientUnitConverter();

    @Test
    void convertsBetweenMassUnits() {
        assertThat(converter.convert(new BigDecimal("1.5"), "g", "MG", "CALCIUM")).hasValueSatisfying(
                v -> assertThat(v).isEqualByComparingTo("1500"));
        assertThat(converter.convert(new BigDecimal("250"), "µg", "MG", "FLUORIDE")).hasValueSatisfying(
                v -> assertThat(v).isEqualByComparingTo("0.25"));
        assertThat(converter.convert(new BigDecimal("0.002"), "MG", "mcg", "SELENIUM")).hasValueSatisfying(
                v -> assertThat(v).isEqualByComparingTo("2"));
    }

    @Test
    void convertsKilojoulesToKilocalories() {
        assertThat(converter.convert(new BigDecimal("418.4"), "kJ", "KCAL", "ENERGY_KCAL")).hasValueSatisfying(
                v -> assertThat(v).isEqualByComparingTo("100"));
    }

    @Test
    void convertsVitaminDInternationalUnitsOnly() {
        assertThat(converter.convert(new BigDecimal("400"), "IU", "UG", "VITAMIN_D")).hasValueSatisfying(
                v -> assertThat(v).isEqualByComparingTo("10"));
        assertThat(converter.convert(new BigDecimal("400"), "IU", "UG", "VITAMIN_A_RAE")).isEmpty();
    }

    @Test
    void refusesIncompatibleUnits() {
        assertThat(converter.convert(BigDecimal.ONE, "KCAL", "G", "ENERGY_KCAL")).isEmpty();
        assertThat(converter.convert(BigDecimal.ONE, "G", "KCAL", "PROTEIN")).isEmpty();
        assertThat(converter.convert(BigDecimal.ONE, "ML", "G", "WATER")).isEmpty();
    }
}
