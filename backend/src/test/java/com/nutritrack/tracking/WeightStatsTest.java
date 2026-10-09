package com.nutritrack.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeightStatsTest {
    private static WeightLog log(String date, String kg) {
        WeightLog l = new WeightLog(1L);
        l.setLogDate(LocalDate.parse(date));
        l.setWeightKg(new BigDecimal(kg));
        return l;
    }

    @Test
    void statsOverRange() {
        var stats = WeightService.stats(List.of(log("2026-09-20", "73.40"), log("2026-09-27", "72.90"),
                log("2026-10-04", "72.20"), log("2026-10-08", "71.80")), LocalDate.parse("2026-10-09"),
                new BigDecimal("68.0"));
        assertThat(stats.latestKg()).isEqualByComparingTo("71.8");
        assertThat(stats.changeKg()).isEqualByComparingTo("-1.6");
        assertThat(stats.lowestKg()).isEqualByComparingTo("71.8");
        assertThat(stats.highestKg()).isEqualByComparingTo("73.4");
        assertThat(stats.sevenDayAverageKg()).isEqualByComparingTo("72.00");
        assertThat(stats.toTargetKg()).isEqualByComparingTo("-3.8");
    }

    @Test
    void emptyRange() {
        var stats = WeightService.stats(List.of(), LocalDate.parse("2026-10-09"), null);
        assertThat(stats.latestKg()).isNull();
        assertThat(stats.sevenDayAverageKg()).isNull();
    }
}
