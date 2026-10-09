package com.nutritrack.target;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutritrack.common.Enums.Sex;
import com.nutritrack.target.ReferenceValueRepository.ReferenceRow;
import com.nutritrack.target.TargetResolver.Override;
import com.nutritrack.target.TargetResolver.ProfileInput;
import com.nutritrack.target.TargetResolver.Resolved;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TargetResolverTest {
    private static ReferenceRow row(String code, String sex, int min, int max, String type, String amount,
                                    String appliesTo) {
        return new ReferenceRow(code, "NASEM_DRI", sex, min, max, "GENERAL", type, new BigDecimal(amount), appliesTo,
                "NASEM", null, "test", null);
    }

    private static final List<ReferenceRow> IRON = List.of(
            row("IRON", "MALE", 19, 30, "RDA", "8", "TOTAL_INTAKE"),
            row("IRON", "FEMALE", 19, 30, "RDA", "18", "TOTAL_INTAKE"),
            row("IRON", "MALE", 19, 30, "UL", "45", "TOTAL_INTAKE"),
            row("IRON", "FEMALE", 19, 30, "UL", "45", "TOTAL_INTAKE"));

    private static ProfileInput profile(Integer age, Sex sex, Integer kcal) {
        return new ProfileInput(age, sex, kcal, null, null, null, null);
    }

    @Test
    void picksRowByAgeAndSex() {
        Resolved r = TargetResolver.resolve("IRON", profile(25, Sex.FEMALE, null), IRON, null);
        assertThat(r.target()).isEqualByComparingTo("18");
        assertThat(r.targetType()).isEqualTo("RDA");
        assertThat(r.upperLimit()).isEqualByComparingTo("45");
        assertThat(r.upperLimitComparable()).isTrue();
        assertThat(r.targetSource().name()).isEqualTo("NASEM");
    }

    @Test
    void withoutSexOnlySharedValuesAreUsed() {
        Resolved r = TargetResolver.resolve("IRON", profile(25, null, null), IRON, null);
        assertThat(r.target()).isNull();
        assertThat(r.notes()).anyMatch(n -> n.contains("differ by sex"));
        // The UL is the same for both sexes, so it is still shown.
        assertThat(r.upperLimit()).isEqualByComparingTo("45");
    }

    @Test
    void withoutAgeNothingIsGuessed() {
        Resolved r = TargetResolver.resolve("IRON", profile(null, Sex.MALE, null), IRON, null);
        assertThat(r.target()).isNull();
        assertThat(r.notes()).anyMatch(n -> n.contains("age"));
    }

    @Test
    void customValuesWin() {
        Resolved r = TargetResolver.resolve("IRON", profile(25, Sex.MALE, null), IRON,
                new Override(new BigDecimal("12"), null));
        assertThat(r.target()).isEqualByComparingTo("12");
        assertThat(r.targetType()).isEqualTo("CUSTOM");
        assertThat(r.customized()).isTrue();
        assertThat(r.upperLimitType()).isEqualTo("UL");
    }

    @Test
    void profileMacrosBeatReferenceRows() {
        List<ReferenceRow> protein = List.of(row("PROTEIN", "MALE", 19, 30, "RDA", "56", "TOTAL_INTAKE"));
        ProfileInput p = new ProfileInput(25, Sex.MALE, 2200, new BigDecimal("90"), null, null, null);
        assertThat(TargetResolver.resolve("PROTEIN", p, protein, null).target()).isEqualByComparingTo("90");
        assertThat(TargetResolver.resolve("ENERGY_KCAL", p, List.of(), null).target()).isEqualByComparingTo("2200");
        assertThat(TargetResolver.resolve("ENERGY_KCAL", profile(25, Sex.MALE, null), List.of(), null).notes())
                .anyMatch(n -> n.contains("calorie target"));
    }

    @Test
    void supplementOnlyUpperLimitIsNotComparable() {
        List<ReferenceRow> mg = List.of(row("MAGNESIUM", "MALE", 19, 30, "RDA", "400", "TOTAL_INTAKE"),
                row("MAGNESIUM", "MALE", 19, 30, "UL", "350", "SUPPLEMENTAL_ONLY"));
        Resolved r = TargetResolver.resolve("MAGNESIUM", profile(25, Sex.MALE, null), mg, null);
        assertThat(r.upperLimit()).isEqualByComparingTo("350");
        assertThat(r.upperLimitComparable()).isFalse();
    }

    @Test
    void cdrrIsUsedWhenThereIsNoUl() {
        List<ReferenceRow> na = List.of(row("SODIUM", "MALE", 19, 30, "AI", "1500", "TOTAL_INTAKE"),
                row("SODIUM", "MALE", 19, 30, "CDRR", "2300", "TOTAL_INTAKE"));
        Resolved r = TargetResolver.resolve("SODIUM", profile(25, Sex.MALE, null), na, null);
        assertThat(r.targetType()).isEqualTo("AI");
        assertThat(r.upperLimitType()).isEqualTo("CDRR");
        assertThat(r.upperLimit()).isEqualByComparingTo("2300");
    }

    @Test
    void percentOfEnergyLimitsConvertToGrams() {
        List<ReferenceRow> sat = List.of(row("FAT_SATURATED", "ANY", 14, 120, "LIMIT_PCT_ENERGY", "10", "TOTAL_INTAKE"));
        List<ReferenceRow> sugar = List.of(row("SUGARS_ADDED", "ANY", 14, 120, "LIMIT_PCT_ENERGY", "10", "TOTAL_INTAKE"));
        assertThat(TargetResolver.resolve("FAT_SATURATED", profile(null, null, 2000), sat, null).upperLimit())
                .isEqualByComparingTo("22.2");
        assertThat(TargetResolver.resolve("SUGARS_ADDED", profile(null, null, 2000), sugar, null).upperLimit())
                .isEqualByComparingTo("50.0");
        Resolved noKcal = TargetResolver.resolve("SUGARS_ADDED", profile(30, Sex.MALE, null), sugar, null);
        assertThat(noKcal.upperLimit()).isNull();
        assertThat(noKcal.notes()).anyMatch(n -> n.contains("10% of calories"));
    }
}
