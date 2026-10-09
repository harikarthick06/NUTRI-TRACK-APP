package com.nutritrack.food;

import com.nutritrack.food.FoodDtos.EnergyCheck;
import com.nutritrack.nutrient.NutrientCodes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Compares reported energy with a general-factor estimate: 4 kcal/g protein, 9 kcal/g fat, 4 kcal/g available
 * carbohydrate and 2 kcal/g fibre. The reported value always stays authoritative; databases use
 * food-specific factors and some foods contain organic acids, polyols or alcohol that this estimate ignores.
 */
@Component
public class EnergyValidator {
    static final BigDecimal TOLERANCE_PCT = new BigDecimal("15");

    public EnergyCheck check(Map<String, BigDecimal> perBasis) {
        BigDecimal reported = perBasis.get(NutrientCodes.ENERGY);
        BigDecimal protein = perBasis.get(NutrientCodes.PROTEIN);
        BigDecimal fat = perBasis.get(NutrientCodes.FAT);
        BigDecimal fiber = perBasis.get(NutrientCodes.FIBER);
        BigDecimal available = perBasis.get(NutrientCodes.AVAILABLE_CARBOHYDRATE);
        if (available == null && perBasis.get(NutrientCodes.CARBOHYDRATE) != null && fiber != null) {
            available = perBasis.get(NutrientCodes.CARBOHYDRATE).subtract(fiber).max(BigDecimal.ZERO);
        }
        if (reported == null || protein == null || fat == null || available == null) {
            return new EnergyCheck(reported, null, null, "INSUFFICIENT_DATA",
                    "Protein, fat and carbohydrate are all needed to cross-check energy.");
        }
        BigDecimal estimate = protein.multiply(BigDecimal.valueOf(4))
                .add(fat.multiply(BigDecimal.valueOf(9)))
                .add(available.multiply(BigDecimal.valueOf(4)))
                .add(fiber == null ? BigDecimal.ZERO : fiber.multiply(BigDecimal.valueOf(2)))
                .setScale(1, RoundingMode.HALF_UP);
        if (reported.signum() == 0 && estimate.signum() == 0) {
            return new EnergyCheck(reported, estimate, BigDecimal.ZERO, "CONSISTENT", "No energy-yielding nutrients.");
        }
        BigDecimal base = reported.signum() == 0 ? estimate : reported;
        BigDecimal diffPct = estimate.subtract(reported).abs().multiply(BigDecimal.valueOf(100))
                .divide(base, 1, RoundingMode.HALF_UP);
        boolean consistent = diffPct.compareTo(TOLERANCE_PCT) <= 0 || estimate.subtract(reported).abs()
                .compareTo(BigDecimal.valueOf(10)) <= 0;
        return new EnergyCheck(reported, estimate, diffPct, consistent ? "CONSISTENT" : "DIFFERS",
                consistent ? "Reported energy matches a 4/4/9 estimate."
                        : "Reported energy differs from a 4/4/9 estimate. The reported value is kept: databases use "
                                + "food-specific conversion factors, and organic acids, polyols or alcohol are not in "
                                + "this estimate.");
    }
}
