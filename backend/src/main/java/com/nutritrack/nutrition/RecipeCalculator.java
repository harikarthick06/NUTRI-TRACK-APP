package com.nutritrack.nutrition;

import com.nutritrack.nutrient.NutrientCodes;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculates recipe nutrients from ingredients.
 *
 * <p>Each ingredient contributes amountPerBasis x (ingredient amount / basis quantity). The recipe total is
 * divided by the cooked weight (or, if not weighed, the sum of ingredient weights) to give values per 100 g of
 * the finished dish. A nutrient is reported only when ingredients with data for it make up at least
 * {@link #MIN_COVERAGE_PCT}% of the recipe's ingredient mass; the coverage is reported with the value because it
 * is then a lower bound. Water is handled by mass balance when the cooked weight is known, so evaporation or
 * absorbed cooking water is reflected.
 *
 * <p>Cooking losses of heat-sensitive vitamins are not modelled. The recipe is logged as one food, so its
 * ingredients are never counted a second time.
 */
public final class RecipeCalculator {
    public static final BigDecimal MIN_COVERAGE_PCT = new BigDecimal("90");
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RecipeCalculator() {
    }

    /**
     * One ingredient. {@code nutrients} are per {@code basisQuantity} of the food; {@code basisAmount} is how much
     * of the food's basis unit is used; {@code grams} is that amount as a mass. {@code coveragePct} carries the
     * coverage of nested recipe foods (null means fully known).
     */
    public record Ingredient(long foodId, String name, BigDecimal basisQuantity, BigDecimal basisAmount,
                             BigDecimal grams, Map<String, BigDecimal> nutrients, Map<String, BigDecimal> coveragePct) {
    }

    public record NutrientValue(BigDecimal per100g, BigDecimal perServing, BigDecimal total, BigDecimal coveragePct) {
    }

    public record Result(BigDecimal ingredientWeightG, BigDecimal cookedWeightG, BigDecimal servings,
                         BigDecimal servingWeightG, Map<String, NutrientValue> nutrients, List<String> missingNutrients,
                         List<String> warnings) {
    }

    public static Result calculate(List<Ingredient> ingredients, BigDecimal cookedWeightG, BigDecimal servings,
                                   List<String> nutrientCodesInOrder) {
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("A recipe needs at least one ingredient");
        }
        if (servings == null || servings.signum() <= 0) {
            throw new IllegalArgumentException("Servings must be greater than zero");
        }
        List<String> warnings = new ArrayList<>();
        BigDecimal ingredientMass = ingredients.stream().map(Ingredient::grams).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finalWeight = cookedWeightG != null && cookedWeightG.signum() > 0 ? cookedWeightG : ingredientMass;

        Map<String, NutrientValue> out = new LinkedHashMap<>();
        Set<String> missing = new LinkedHashSet<>();
        for (String code : nutrientCodesInOrder) {
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal knownMass = BigDecimal.ZERO;
            for (Ingredient ing : ingredients) {
                BigDecimal per = ing.nutrients().get(code);
                if (per == null) {
                    continue;
                }
                total = total.add(per.multiply(ing.basisAmount(), MC).divide(ing.basisQuantity(), MC), MC);
                BigDecimal cov = ing.coveragePct() == null ? null : ing.coveragePct().get(code);
                BigDecimal share = cov == null ? BigDecimal.ONE : cov.divide(HUNDRED, MC);
                knownMass = knownMass.add(ing.grams().multiply(share, MC), MC);
            }
            BigDecimal coverage = ingredientMass.signum() == 0 ? BigDecimal.ZERO
                    : knownMass.multiply(HUNDRED, MC).divide(ingredientMass, MC).setScale(2, RoundingMode.HALF_UP);
            if (code.equals(NutrientCodes.WATER) && cookedWeightG != null && cookedWeightG.signum() > 0) {
                if (coverage.compareTo(HUNDRED) < 0) {
                    missing.add(code);
                    continue;
                }
                // Water in the dish = cooked weight - dry matter of the ingredients.
                BigDecimal dryMatter = ingredientMass.subtract(total, MC);
                BigDecimal water = cookedWeightG.subtract(dryMatter, MC);
                if (water.signum() < 0) {
                    warnings.add("The cooked weight is lower than the ingredients' dry matter ("
                            + dryMatter.setScale(0, RoundingMode.HALF_UP) + " g). Check the weight.");
                    missing.add(code);
                    continue;
                }
                total = water;
            }
            if (knownMass.signum() == 0 || coverage.compareTo(MIN_COVERAGE_PCT) < 0) {
                missing.add(code);
                continue;
            }
            BigDecimal per100 = total.multiply(HUNDRED, MC).divide(finalWeight, MC);
            BigDecimal perServing = total.divide(servings, MC);
            out.put(code, new NutrientValue(per100.setScale(6, RoundingMode.HALF_UP),
                    perServing.setScale(6, RoundingMode.HALF_UP), total.setScale(6, RoundingMode.HALF_UP),
                    coverage.compareTo(HUNDRED) >= 0 ? null : coverage));
        }
        if (cookedWeightG == null) {
            warnings.add("No cooked weight entered: values assume the dish weighs the same as its ingredients. "
                    + "Weigh the finished dish for accurate per-serving values.");
        }
        BigDecimal servingWeight = finalWeight.divide(servings, MC).setScale(2, RoundingMode.HALF_UP);
        return new Result(ingredientMass.setScale(2, RoundingMode.HALF_UP), finalWeight.setScale(2, RoundingMode.HALF_UP),
                servings, servingWeight, out, List.copyOf(missing), warnings);
    }
}
