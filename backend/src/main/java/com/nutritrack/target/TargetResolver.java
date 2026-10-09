package com.nutritrack.target;

import com.nutritrack.common.Enums.Sex;
import com.nutritrack.nutrient.NutrientCodes;
import com.nutritrack.target.ReferenceValueRepository.ReferenceRow;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Works out each nutrient's daily target and upper limit for one person.
 *
 * <p>Precedence for the target: the user's own value, then the profile's macro targets, then the reference RDA, then
 * the AI. For the upper limit: the user's own value, then the UL, then the CDRR, then a percent-of-energy limit
 * converted to grams with the calorie target. Nothing is guessed: when age or sex is needed to choose between
 * reference rows and is missing, the target is left empty with the reason.
 */
public final class TargetResolver {
    private static final MathContext MC = MathContext.DECIMAL64;
    /** Ages covered by the app (profile age is 14-120). */
    private static final int MIN_AGE = 14;
    private static final int MAX_AGE = 120;

    private TargetResolver() {
    }

    public record ProfileInput(Integer age, Sex sex, Integer calorieTargetKcal, BigDecimal proteinTargetG,
                               BigDecimal carbsTargetG, BigDecimal fatTargetG, BigDecimal fiberTargetG) {
    }

    public record Override(BigDecimal target, BigDecimal upperLimit) {
    }

    public record Source(String name, String url, String version, String valueType, String note) {
    }

    /**
     * {@code upperLimitComparable} is false when the limit only covers part of intake (for example supplements, or
     * preformed vitamin A), so a food-log total must not be compared with it.
     */
    public record Resolved(String code, BigDecimal target, String targetType, Source targetSource,
                           BigDecimal upperLimit, String upperLimitType, String upperLimitAppliesTo,
                           boolean upperLimitComparable, Source upperLimitSource, boolean customized,
                           List<String> notes) {
    }

    public static Resolved resolve(String code, ProfileInput profile, List<ReferenceRow> rowsForNutrient,
                                   Override override) {
        List<String> notes = new ArrayList<>();
        BigDecimal target = null;
        String targetType = null;
        Source targetSource = null;
        boolean customized = false;

        BigDecimal profileValue = profileMacro(code, profile);
        if (override != null && override.target() != null) {
            target = override.target();
            targetType = "CUSTOM";
            customized = true;
        } else if (profileValue != null) {
            target = profileValue;
            targetType = "PROFILE";
        } else {
            for (String type : List.of("RDA", "AI")) {
                Pick pick = pick(rowsForNutrient, type, profile);
                if (pick.row != null) {
                    target = pick.row.amount();
                    targetType = type;
                    targetSource = source(pick.row);
                    break;
                }
                if (pick.reason != null && !notes.contains(pick.reason)) {
                    notes.add(pick.reason);
                }
            }
            if (target == null && code.equals(NutrientCodes.ENERGY)) {
                notes.add("Set a calorie target in your profile");
            }
        }

        BigDecimal upper = null;
        String upperType = null;
        String appliesTo = null;
        Source upperSource = null;
        if (override != null && override.upperLimit() != null) {
            upper = override.upperLimit();
            upperType = "CUSTOM";
            appliesTo = "TOTAL_INTAKE";
            customized = true;
        } else {
            for (String type : List.of("UL", "CDRR", "LIMIT_PCT_ENERGY")) {
                Pick pick = pick(rowsForNutrient, type, profile);
                if (pick.row == null) {
                    if (pick.reason != null && !notes.contains(pick.reason)) {
                        notes.add(pick.reason);
                    }
                    continue;
                }
                BigDecimal amount = pick.row.amount();
                if (type.equals("LIMIT_PCT_ENERGY")) {
                    amount = percentOfEnergyToGrams(code, amount, profile.calorieTargetKcal());
                    if (amount == null) {
                        notes.add("Set a calorie target to see the limit in grams (" + pick.row.amount()
                                .stripTrailingZeros().toPlainString() + "% of calories)");
                        continue;
                    }
                }
                upper = amount;
                upperType = type;
                appliesTo = pick.row.appliesTo();
                upperSource = source(pick.row);
                break;
            }
        }
        boolean comparable = upper != null && "TOTAL_INTAKE".equals(appliesTo);
        return new Resolved(code, target, targetType, targetSource, upper, upperType, appliesTo, comparable,
                upperSource, customized, List.copyOf(notes));
    }

    /** Groups reference rows by nutrient code. */
    public static Map<String, List<ReferenceRow>> byNutrient(List<ReferenceRow> rows) {
        Map<String, List<ReferenceRow>> out = new LinkedHashMap<>();
        for (ReferenceRow r : rows) {
            out.computeIfAbsent(r.nutrientCode(), k -> new ArrayList<>()).add(r);
        }
        return out;
    }

    private record Pick(ReferenceRow row, String reason) {
    }

    private static Pick pick(List<ReferenceRow> rows, String valueType, ProfileInput profile) {
        List<ReferenceRow> ofType = rows.stream()
                .filter(r -> r.valueType().equals(valueType) && "GENERAL".equals(r.lifeStage()))
                .toList();
        if (ofType.isEmpty()) {
            return new Pick(null, null);
        }
        List<ReferenceRow> byAge;
        if (profile.age() != null) {
            byAge = ofType.stream().filter(r -> r.ageMin() <= profile.age() && r.ageMax() >= profile.age()).toList();
        } else {
            byAge = ofType.stream().filter(r -> r.ageMin() <= MIN_AGE && r.ageMax() >= MAX_AGE).toList();
            if (byAge.isEmpty()) {
                return new Pick(null, "Add your age in your profile to see reference values");
            }
        }
        if (byAge.isEmpty()) {
            return new Pick(null, null);
        }
        Optional<ReferenceRow> any = byAge.stream().filter(r -> "ANY".equals(r.sex())).findFirst();
        if (any.isPresent()) {
            return new Pick(any.get(), null);
        }
        if (profile.sex() != null) {
            return new Pick(byAge.stream().filter(r -> r.sex().equals(profile.sex().name())).findFirst().orElse(null),
                    null);
        }
        // No sex in the profile: only use the value when it is the same for both.
        List<BigDecimal> distinct = byAge.stream().map(ReferenceRow::amount)
                .map(BigDecimal::stripTrailingZeros).distinct().toList();
        if (distinct.size() == 1) {
            return new Pick(byAge.get(0), null);
        }
        return new Pick(null, "Reference values differ by sex; set it in your profile to see them");
    }

    private static BigDecimal profileMacro(String code, ProfileInput p) {
        return switch (code) {
            case NutrientCodes.ENERGY -> p.calorieTargetKcal() == null ? null : BigDecimal.valueOf(p.calorieTargetKcal());
            case NutrientCodes.PROTEIN -> p.proteinTargetG();
            case NutrientCodes.CARBOHYDRATE -> p.carbsTargetG();
            case NutrientCodes.FAT -> p.fatTargetG();
            case NutrientCodes.FIBER -> p.fiberTargetG();
            default -> null;
        };
    }

    /** A "less than X% of calories" limit in grams: fats at 9 kcal/g, sugars at 4 kcal/g. */
    static BigDecimal percentOfEnergyToGrams(String code, BigDecimal pct, Integer calorieTarget) {
        if (calorieTarget == null || calorieTarget <= 0) {
            return null;
        }
        BigDecimal kcalPerGram = code.startsWith("FAT") ? BigDecimal.valueOf(9) : BigDecimal.valueOf(4);
        return pct.multiply(BigDecimal.valueOf(calorieTarget), MC).divide(BigDecimal.valueOf(100), MC)
                .divide(kcalPerGram, MC).setScale(1, RoundingMode.HALF_UP);
    }

    private static Source source(ReferenceRow r) {
        return new Source(r.sourceName(), r.sourceUrl(), r.sourceVersion(), r.valueType(), r.notes());
    }
}
