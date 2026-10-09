package com.nutritrack.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutritrack.common.ApiException;
import com.nutritrack.nutrient.NutrientDefinition;
import com.nutritrack.nutrition.NutritionAggregationRepository.Total;
import com.nutritrack.report.ReportService.NutrientAverage;
import com.nutritrack.target.TargetResolver.Resolved;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportMathTest {
    private static final NutrientDefinition VIT_C = new NutrientDefinition(1, "VITAMIN_C", "Vitamin C", "MG", "VITAMIN",
            1, null);
    private static final LocalDate D1 = LocalDate.parse("2026-10-01");
    private static final LocalDate D2 = LocalDate.parse("2026-10-02");
    private static final LocalDate D3 = LocalDate.parse("2026-10-03");

    private static Resolved target(String t, String upper) {
        return new Resolved("VITAMIN_C", new BigDecimal(t), "RDA", null, new BigDecimal(upper), "UL", "TOTAL_INTAKE",
                true, null, false, List.of());
    }

    @Test
    void averageIsOverDaysWithDataOnly() {
        Map<LocalDate, Map<String, Total>> totals = Map.of(
                D1, Map.of("VITAMIN_C", new Total("VITAMIN_C", new BigDecimal("60"), 2, 0)),
                D2, Map.of("VITAMIN_C", new Total("VITAMIN_C", new BigDecimal("2100"), 1, 0)));
        NutrientAverage a = ReportService.average(VIT_C, List.of(D1, D2, D3), totals, target("90", "2000"));
        // Day 3 was logged but had no vitamin C data: unknown, not zero.
        assertThat(a.daysWithData()).isEqualTo(2);
        assertThat(a.loggedDays()).isEqualTo(3);
        assertThat(a.average()).isEqualByComparingTo("1080");
        assertThat(a.percentOfTarget()).isEqualByComparingTo("1200");
        assertThat(a.daysAboveUpperLimit()).isEqualTo(1);
    }

    @Test
    void noDataGivesNoAverage() {
        NutrientAverage a = ReportService.average(VIT_C, List.of(D1), Map.of(), target("90", "2000"));
        assertThat(a.average()).isNull();
        assertThat(a.percentOfTarget()).isNull();
    }

    @Test
    void macroSplitNeedsCompleteMacros() {
        List<NutrientAverage> full = List.of(avg("PROTEIN", "100", 3, 3), avg("CARBOHYDRATE", "250", 3, 3),
                avg("FAT_TOTAL", "60", 3, 3));
        ReportService.MacroSplit split = ReportService.macroSplit(full);
        // 400 + 1000 + 540 = 1940 kcal
        assertThat(split.proteinPct()).isEqualByComparingTo("20.6");
        assertThat(split.carbsPct()).isEqualByComparingTo("51.5");
        assertThat(split.fatPct()).isEqualByComparingTo("27.8");
        List<NutrientAverage> partial = List.of(avg("PROTEIN", "100", 3, 3), avg("CARBOHYDRATE", "250", 2, 3),
                avg("FAT_TOTAL", "60", 3, 3));
        assertThat(ReportService.macroSplit(partial)).isNull();
    }

    @Test
    void calorieToleranceIsTenPercent() {
        assertThat(ReportService.withinTolerance(new BigDecimal("1980"), new BigDecimal("2200"))).isTrue();
        assertThat(ReportService.withinTolerance(new BigDecimal("1979"), new BigDecimal("2200"))).isFalse();
    }

    @Test
    void rangeLimits() {
        assertThatThrownBy(() -> ReportService.checkRange(D2, D1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ReportService.checkRange(D1, D1.plusDays(400))).isInstanceOf(ApiException.class);
        ReportService.checkRange(D1, D1.plusDays(366));
    }

    private static NutrientAverage avg(String code, String amount, int withData, int logged) {
        return new NutrientAverage(code, code, "G", "MACRONUTRIENT", new BigDecimal(amount), withData, logged, null,
                null, null, null, false, 0);
    }
}
