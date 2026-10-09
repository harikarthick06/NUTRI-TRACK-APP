package com.nutritrack.food;

import com.nutritrack.nutrient.NutrientCodes;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Fills in values that follow arithmetically from other reported values of the same food, and labels
 * them DERIVED. Nothing is estimated from other foods.
 */
public final class NutrientDerivations {
    private NutrientDerivations() {
    }

    /** @param values nutrient code to amount; @param origins nutrient code to value origin (updated in place). */
    public static void fill(Map<String, BigDecimal> values, Map<String, String> origins) {
        BigDecimal total = values.get(NutrientCodes.CARBOHYDRATE);
        BigDecimal available = values.get(NutrientCodes.AVAILABLE_CARBOHYDRATE);
        BigDecimal fiber = values.get(NutrientCodes.FIBER);
        if (total == null && available != null && fiber != null) {
            values.put(NutrientCodes.CARBOHYDRATE, available.add(fiber));
            origins.put(NutrientCodes.CARBOHYDRATE, FoodNutrient.DERIVED);
        } else if (available == null && total != null && fiber != null && total.compareTo(fiber) >= 0) {
            values.put(NutrientCodes.AVAILABLE_CARBOHYDRATE, total.subtract(fiber));
            origins.put(NutrientCodes.AVAILABLE_CARBOHYDRATE, FoodNutrient.DERIVED);
        }
    }
}
