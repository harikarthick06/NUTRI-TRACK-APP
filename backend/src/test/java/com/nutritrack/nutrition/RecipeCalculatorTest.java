package com.nutritrack.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutritrack.nutrition.RecipeCalculator.Ingredient;
import com.nutritrack.nutrition.RecipeCalculator.Result;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipeCalculatorTest {
    private static final List<String> CODES = List.of("ENERGY_KCAL", "PROTEIN", "VITAMIN_C", "WATER");

    private static Ingredient ing(long id, String grams, Map<String, String> per100) {
        Map<String, BigDecimal> n = new HashMap<>();
        per100.forEach((k, v) -> n.put(k, new BigDecimal(v)));
        BigDecimal g = new BigDecimal(grams);
        return new Ingredient(id, "food" + id, new BigDecimal("100"), g, g, n, null);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void sumsIngredientsAndDividesByCookedWeightAndServings() {
        // 200 g raw rice (350 kcal, 12 g water /100 g) + 100 g dal (340 kcal, 10 g water) cooked to 900 g.
        Result r = RecipeCalculator.calculate(List.of(
                        ing(1, "200", Map.of("ENERGY_KCAL", "350", "PROTEIN", "7", "WATER", "12")),
                        ing(2, "100", Map.of("ENERGY_KCAL", "340", "PROTEIN", "24", "WATER", "10"))),
                bd("900"), bd("3"), CODES);
        assertThat(r.nutrients().get("ENERGY_KCAL").total()).isEqualByComparingTo("1040");
        assertThat(r.nutrients().get("ENERGY_KCAL").perServing()).isEqualByComparingTo("346.666667");
        assertThat(r.nutrients().get("ENERGY_KCAL").per100g()).isEqualByComparingTo("115.555556");
        assertThat(r.servingWeightG()).isEqualByComparingTo("300");
        // Water by mass balance: dry matter = 300 - (24 + 10) = 266 g, so water = 900 - 266 = 634 g.
        assertThat(r.nutrients().get("WATER").total()).isEqualByComparingTo("634");
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void missingValueIsUnknownNotZero() {
        // Only 50% of the mass has vitamin C data: below the 90% threshold, so it is not reported at all.
        Result r = RecipeCalculator.calculate(List.of(
                        ing(1, "100", Map.of("ENERGY_KCAL", "100", "VITAMIN_C", "20")),
                        ing(2, "100", Map.of("ENERGY_KCAL", "100"))),
                bd("200"), bd("2"), CODES);
        assertThat(r.nutrients()).doesNotContainKey("VITAMIN_C");
        assertThat(r.missingNutrients()).contains("VITAMIN_C", "PROTEIN");
    }

    @Test
    void highCoverageIsReportedWithItsCoverage() {
        // 95 g of 100 g known: reported, flagged with 95% coverage.
        Result r = RecipeCalculator.calculate(List.of(
                        ing(1, "95", Map.of("ENERGY_KCAL", "100", "VITAMIN_C", "10")),
                        ing(2, "5", Map.of("ENERGY_KCAL", "900"))),
                bd("100"), bd("1"), CODES);
        assertThat(r.nutrients().get("VITAMIN_C").total()).isEqualByComparingTo("9.5");
        assertThat(r.nutrients().get("VITAMIN_C").coveragePct()).isEqualByComparingTo("95");
        assertThat(r.nutrients().get("ENERGY_KCAL").coveragePct()).isNull();
    }

    @Test
    void waterNeedsFullCoverageAndPlausibleCookedWeight() {
        Result r = RecipeCalculator.calculate(List.of(ing(1, "100", Map.of("ENERGY_KCAL", "350", "WATER", "10"))),
                bd("50"), bd("1"), CODES);
        assertThat(r.nutrients()).doesNotContainKey("WATER");
        assertThat(r.warnings()).anyMatch(w -> w.contains("dry matter"));
    }

    @Test
    void withoutCookedWeightUsesIngredientMassAndWarns() {
        Result r = RecipeCalculator.calculate(List.of(ing(1, "250", Map.of("ENERGY_KCAL", "40"))), null, bd("2"), CODES);
        assertThat(r.cookedWeightG()).isEqualByComparingTo("250");
        assertThat(r.nutrients().get("ENERGY_KCAL").per100g()).isEqualByComparingTo("40");
        assertThat(r.warnings()).anyMatch(w -> w.contains("No cooked weight"));
    }

    @Test
    void nestedRecipeCoverageCarriesThrough() {
        Map<String, BigDecimal> n = Map.of("VITAMIN_C", bd("5"));
        Ingredient nested = new Ingredient(9, "chutney", bd("100"), bd("100"), bd("100"), n, Map.of("VITAMIN_C", bd("80")));
        Result r = RecipeCalculator.calculate(List.of(nested), bd("100"), bd("1"), CODES);
        assertThat(r.nutrients()).doesNotContainKey("VITAMIN_C");
    }

    @Test
    void rejectsEmptyOrZeroServings() {
        assertThatThrownBy(() -> RecipeCalculator.calculate(List.of(), null, bd("1"), CODES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeCalculator.calculate(List.of(ing(1, "1", Map.of())), null, bd("0"), CODES))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
