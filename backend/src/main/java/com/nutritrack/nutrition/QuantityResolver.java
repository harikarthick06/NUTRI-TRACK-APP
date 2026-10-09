package com.nutritrack.nutrition;

import com.nutritrack.common.ApiException;
import com.nutritrack.common.Enums.BasisUnit;
import com.nutritrack.common.Enums.PreparationState;
import com.nutritrack.common.Enums.WeightState;
import com.nutritrack.food.Food;
import com.nutritrack.food.FoodPortion;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Converts a logged quantity ("2 pieces", "1.5 cups", "180 g cooked") into the amount of the food's own basis
 * unit (grams or millilitres), which is what nutrient scaling uses.
 *
 * <ul>
 *   <li>Mass units convert directly for gram-based foods.</li>
 *   <li>Volume units convert directly for millilitre-based foods. For gram-based foods they need a density,
 *       taken from one of the food's household volume measures (e.g. "1 cup = 158 g").</li>
 *   <li>Piece-like units (piece, serving, slice, bowl) need one of the food's portions.</li>
 *   <li>A cooked weight of a raw or dry food is only accepted when the food has a cooked-yield factor, and
 *       is converted to the raw-equivalent weight. Without a factor the request is rejected rather than
 *       silently applying raw values to a cooked weight.</li>
 * </ul>
 */
@Component
public class QuantityResolver {
    private static final MathContext MC = MathContext.DECIMAL64;

    /** Grams per unit. */
    static final Map<String, BigDecimal> MASS = Map.of(
            "G", BigDecimal.ONE,
            "KG", new BigDecimal("1000"),
            "MG", new BigDecimal("0.001"),
            "OZ", new BigDecimal("28.349523125"),
            "LB", new BigDecimal("453.59237"));

    /** Millilitres per unit (US nutrition-labelling cup of 240 mL, 15 mL tablespoon, 5 mL teaspoon). */
    static final Map<String, BigDecimal> VOLUME = Map.of(
            "ML", BigDecimal.ONE,
            "L", new BigDecimal("1000"),
            "CUP", new BigDecimal("240"),
            "TBSP", new BigDecimal("15"),
            "TSP", new BigDecimal("5"));

    static final List<String> PIECE_UNITS = List.of("PIECE", "SERVING", "SLICE", "BOWL");

    /** {@code portionId} is the portion that was used, including one picked by unit (null for mass/volume). */
    public record Resolved(BigDecimal basisAmount, String explanation, Long portionId) {
        public Resolved(BigDecimal basisAmount, String explanation) {
            this(basisAmount, explanation, null);
        }
    }

    public Resolved resolve(Food food, List<FoodPortion> portions, BigDecimal quantity, String unitRaw, Long portionId,
                            WeightState weightState) {
        if (quantity == null || quantity.signum() <= 0) {
            throw ApiException.badRequest("INVALID_QUANTITY", "Quantity must be greater than zero");
        }
        String unit = unitRaw == null ? "" : unitRaw.trim().toUpperCase(Locale.ROOT);
        WeightState state = weightState == null ? WeightState.AS_DEFINED : weightState;
        BigDecimal basis;
        String explanation;
        Long usedPortion = null;

        if (state == WeightState.COOKED_WEIGHT_OF_RAW_FOOD) {
            return resolveCookedWeight(food, quantity, unit);
        }

        if (portionId != null) {
            FoodPortion portion = portions.stream().filter(p -> p.getId().equals(portionId)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("INVALID_PORTION", "That portion does not belong to this food"));
            basis = quantity.multiply(portion.getBasisAmount(), MC).divide(portion.getUnitQuantity(), MC);
            explanation = quantity.stripTrailingZeros().toPlainString() + " x " + portion.getLabel();
            usedPortion = portion.getId();
        } else if (MASS.containsKey(unit)) {
            BigDecimal grams = quantity.multiply(MASS.get(unit), MC);
            if (food.getBasisUnit() == BasisUnit.G) {
                basis = grams;
            } else {
                BigDecimal mlPerGram = density(portions).map(d -> BigDecimal.ONE.divide(d, MC))
                        .orElseThrow(() -> noConversion(food, unit));
                basis = grams.multiply(mlPerGram, MC);
            }
            explanation = quantity.stripTrailingZeros().toPlainString() + " " + unit.toLowerCase(Locale.ROOT);
        } else if (VOLUME.containsKey(unit)) {
            BigDecimal ml = quantity.multiply(VOLUME.get(unit), MC);
            if (food.getBasisUnit() == BasisUnit.ML) {
                basis = ml;
            } else {
                Optional<FoodPortion> exact = portions.stream().filter(p -> p.getUnit().equals(unit)).findFirst();
                if (exact.isPresent()) {
                    FoodPortion p = exact.get();
                    basis = quantity.multiply(p.getBasisAmount(), MC).divide(p.getUnitQuantity(), MC);
                } else {
                    basis = ml.multiply(density(portions).orElseThrow(() -> noConversion(food, unit)), MC);
                }
            }
            explanation = quantity.stripTrailingZeros().toPlainString() + " " + unit.toLowerCase(Locale.ROOT);
        } else if (PIECE_UNITS.contains(unit)) {
            FoodPortion portion = portions.stream().filter(p -> p.getUnit().equals(unit))
                    .sorted((a, b) -> Boolean.compare(b.isDefaultPortion(), a.isDefaultPortion()))
                    .findFirst()
                    .orElseThrow(() -> noConversion(food, unit));
            basis = quantity.multiply(portion.getBasisAmount(), MC).divide(portion.getUnitQuantity(), MC);
            explanation = quantity.stripTrailingZeros().toPlainString() + " x " + portion.getLabel();
            usedPortion = portion.getId();
        } else {
            throw ApiException.badRequest("UNKNOWN_UNIT", "Unknown unit '" + unitRaw + "'");
        }
        return new Resolved(basis.setScale(3, RoundingMode.HALF_UP), explanation, usedPortion);
    }

    private Resolved resolveCookedWeight(Food food, BigDecimal quantity, String unit) {
        if (food.getPreparationState() != PreparationState.RAW && food.getPreparationState() != PreparationState.DRY) {
            throw ApiException.badRequest("NOT_A_RAW_FOOD",
                    "Cooked-weight conversion only applies to raw or dry foods; log this food's weight as is");
        }
        if (!MASS.containsKey(unit) || food.getBasisUnit() != BasisUnit.G) {
            throw ApiException.badRequest("COOKED_WEIGHT_NEEDS_MASS", "Enter the cooked weight in grams");
        }
        BigDecimal yield = food.getCookedYieldFactor();
        if (yield == null || yield.signum() <= 0) {
            throw ApiException.unprocessable("NO_COOKED_YIELD", "'" + food.getName() + "' has no raw-to-cooked conversion. "
                    + "Search for a cooked version of this food, or enter the raw weight instead.");
        }
        BigDecimal cookedGrams = quantity.multiply(MASS.get(unit), MC);
        BigDecimal raw = cookedGrams.divide(yield, MC).setScale(3, RoundingMode.HALF_UP);
        return new Resolved(raw, cookedGrams.stripTrailingZeros().toPlainString() + " g cooked = "
                + raw.setScale(0, RoundingMode.HALF_UP).toPlainString() + " g raw (yield x" + yield.stripTrailingZeros()
                .toPlainString() + ", estimated)");
    }

    /** Grams per millilitre from the food's household volume measures, if it has one. */
    static Optional<BigDecimal> density(List<FoodPortion> portions) {
        return portions.stream().filter(p -> VOLUME.containsKey(p.getUnit()))
                .findFirst()
                .map(p -> p.getBasisAmount().divide(p.getUnitQuantity().multiply(VOLUME.get(p.getUnit()), MC), MC));
    }

    private static ApiException noConversion(Food food, String unit) {
        return ApiException.unprocessable("NO_UNIT_CONVERSION", "'" + food.getName() + "' has no conversion for "
                + unit.toLowerCase(Locale.ROOT) + ". Use grams" + (food.getBasisUnit() == BasisUnit.ML ? " or mL" : "")
                + " or one of the listed portions.");
    }

    /** Units a client can offer for a food, given its basis and portions. */
    public static List<String> supportedUnits(Food food, List<FoodPortion> portions) {
        var units = new java.util.LinkedHashSet<String>();
        boolean hasDensity = density(portions).isPresent();
        if (food.getBasisUnit() == BasisUnit.G || hasDensity) {
            units.addAll(List.of("G", "KG", "OZ"));
        }
        if (food.getBasisUnit() == BasisUnit.ML || hasDensity) {
            units.addAll(List.of("ML", "L", "CUP", "TBSP", "TSP"));
        }
        portions.forEach(p -> units.add(p.getUnit()));
        return List.copyOf(units);
    }
}
