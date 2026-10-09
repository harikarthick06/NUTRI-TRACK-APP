package com.nutritrack.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutritrack.nutrient.NutrientDefinition;
import com.nutritrack.nutrition.NutritionAggregationRepository.Total;
import com.nutritrack.nutrition.NutritionSummaryService.DataCoverage;
import com.nutritrack.nutrition.NutritionSummaryService.NutrientStatus;
import com.nutritrack.nutrition.NutritionSummaryService.TargetStatus;
import com.nutritrack.target.TargetResolver.Resolved;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class NutritionStatusTest {
    private static final NutrientDefinition IRON = new NutrientDefinition(1, "IRON", "Iron", "MG", "MINERAL", 1, null);

    private static Resolved target(String target, String upper, String appliesTo) {
        return new Resolved("IRON", target == null ? null : new BigDecimal(target), "RDA", null,
                upper == null ? null : new BigDecimal(upper), upper == null ? null : "UL", appliesTo,
                upper != null && "TOTAL_INTAKE".equals(appliesTo), null, false, List.of());
    }

    private static Total total(String amount, int withData, int partial) {
        return new Total("IRON", new BigDecimal(amount), withData, partial);
    }

    @Test
    void noDataIsUnknownNotBelowTarget() {
        NutrientStatus s = NutritionSummaryService.status(IRON, null, 3, target("8", "45", "TOTAL_INTAKE"));
        assertThat(s.amount()).isNull();
        assertThat(s.coverage()).isEqualTo(DataCoverage.NO_DATA);
        assertThat(s.status()).isEqualTo(TargetStatus.UNKNOWN);
    }

    @Test
    void partialDataIsFlagged() {
        NutrientStatus s = NutritionSummaryService.status(IRON, total("4", 2, 0), 3, target("8", null, null));
        assertThat(s.coverage()).isEqualTo(DataCoverage.PARTIAL);
        assertThat(s.percentOfTarget()).isEqualByComparingTo("50");
        assertThat(s.status()).isEqualTo(TargetStatus.BELOW_TARGET);
        assertThat(s.notes()).anyMatch(n -> n.contains("2 of 3"));
    }

    @Test
    void nestedRecipeWithPartialCoverageIsPartial() {
        NutrientStatus s = NutritionSummaryService.status(IRON, total("9", 3, 1), 3, target("8", null, null));
        assertThat(s.coverage()).isEqualTo(DataCoverage.PARTIAL);
        assertThat(s.status()).isEqualTo(TargetStatus.TARGET_REACHED);
    }

    @Test
    void aboveComparableUpperLimit() {
        NutrientStatus s = NutritionSummaryService.status(IRON, total("50", 3, 0), 3, target("8", "45", "TOTAL_INTAKE"));
        assertThat(s.status()).isEqualTo(TargetStatus.ABOVE_UPPER_LIMIT);
        assertThat(s.percentOfUpperLimit()).isEqualByComparingTo("111");
    }

    @Test
    void supplementOnlyLimitNeverTriggersAWarning() {
        NutrientStatus s = NutritionSummaryService.status(IRON, total("500", 3, 0), 3,
                target("400", "350", "SUPPLEMENTAL_ONLY"));
        assertThat(s.status()).isEqualTo(TargetStatus.TARGET_REACHED);
        assertThat(s.percentOfUpperLimit()).isNull();
        assertThat(s.notes()).anyMatch(n -> n.contains("supplements"));
    }

    @Test
    void nothingLogged() {
        NutrientStatus s = NutritionSummaryService.status(IRON, null, 0, target(null, null, null));
        assertThat(s.coverage()).isEqualTo(DataCoverage.NO_ENTRIES);
        assertThat(s.status()).isEqualTo(TargetStatus.NO_TARGET);
    }
}
