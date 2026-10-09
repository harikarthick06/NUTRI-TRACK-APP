package com.nutritrack.nutrient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Converts nutrient amounts between units. Only physically compatible conversions are allowed: mass to
 * mass (g, mg, ug), energy to energy (kcal, kJ), and IU to ug for vitamin D only (1 ug = 40 IU). Vitamin A
 * and vitamin E IU are not converted because the factor depends on the chemical form.
 */
@Component
public class NutrientUnitConverter {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal KJ_PER_KCAL = new BigDecimal("4.184");

    /** Normalises unit spellings used by data providers ("µg", "mcg", "kJ", "KCAL"...). */
    public static String normalizeUnit(String unit) {
        if (unit == null) {
            return "";
        }
        String u = unit.trim().toUpperCase(Locale.ROOT).replace("Μ", "U").replace("µ", "U").replace("μ", "U");
        return switch (u) {
            case "MCG", "UG", "MICROGRAM", "MICROGRAMS" -> "UG";
            case "MG", "MILLIGRAM", "MILLIGRAMS" -> "MG";
            case "G", "GRAM", "GRAMS" -> "G";
            case "KCAL", "CAL" -> "KCAL";
            case "KJ" -> "KJ";
            case "IU" -> "IU";
            case "ML" -> "ML";
            default -> u;
        };
    }

    public Optional<BigDecimal> convert(BigDecimal amount, String fromUnit, String toUnit, String nutrientCode) {
        String from = normalizeUnit(fromUnit);
        String to = normalizeUnit(toUnit);
        if (amount == null) {
            return Optional.empty();
        }
        if (from.equals(to)) {
            return Optional.of(amount);
        }
        BigDecimal fromMass = massFactor(from);
        BigDecimal toMass = massFactor(to);
        if (fromMass != null && toMass != null) {
            return Optional.of(amount.multiply(fromMass, MC).divide(toMass, MC));
        }
        if (from.equals("KJ") && to.equals("KCAL")) {
            return Optional.of(amount.divide(KJ_PER_KCAL, MC));
        }
        if (from.equals("KCAL") && to.equals("KJ")) {
            return Optional.of(amount.multiply(KJ_PER_KCAL, MC));
        }
        if (from.equals("IU") && NutrientCodes.VITAMIN_D.equals(nutrientCode) && toMass != null) {
            BigDecimal ug = amount.divide(BigDecimal.valueOf(40), MC);
            return Optional.of(ug.multiply(massFactor("UG"), MC).divide(toMass, MC));
        }
        return Optional.empty();
    }

    /** Grams per unit, or null for non-mass units. */
    private static BigDecimal massFactor(String unit) {
        return switch (unit) {
            case "G" -> BigDecimal.ONE;
            case "MG" -> new BigDecimal("0.001");
            case "UG" -> new BigDecimal("0.000001");
            default -> null;
        };
    }
}
