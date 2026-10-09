package com.nutritrack.nutrition;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.Map;

/** Scales per-basis nutrient values to a consumed amount. Unknown nutrients stay absent (never zero). */
public final class NutrientScaler {
    private static final MathContext MC = MathContext.DECIMAL64;

    private NutrientScaler() {
    }

    public static BigDecimal factor(BigDecimal basisAmount, BigDecimal basisQuantity) {
        return basisAmount.divide(basisQuantity, MC);
    }

    public static Map<String, BigDecimal> scale(Map<String, BigDecimal> perBasis, BigDecimal basisAmount,
                                                BigDecimal basisQuantity) {
        BigDecimal f = factor(basisAmount, basisQuantity);
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        perBasis.forEach((code, v) -> out.put(code, v.multiply(f, MC)));
        return out;
    }
}
