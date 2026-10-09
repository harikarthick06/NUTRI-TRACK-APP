package com.nutritrack.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutritrack.common.ApiException;
import com.nutritrack.common.Enums.ActivityLevel;
import com.nutritrack.common.Enums.Goal;
import com.nutritrack.common.Enums.Sex;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TargetCalculatorTest {
    private final TargetCalculator calculator = new TargetCalculator();

    private static UserProfile profile(Sex sex, Goal goal) {
        UserProfile p = new UserProfile(1L, "Test");
        p.setAge(30);
        p.setHeightCm(new BigDecimal("175"));
        p.setCurrentWeightKg(new BigDecimal("78"));
        p.setSex(sex);
        p.setActivityLevel(ActivityLevel.MODERATE);
        p.setGoal(goal);
        return p;
    }

    @Test
    void usesMifflinStJeorForMales() {
        var s = calculator.suggest(profile(Sex.MALE, Goal.MAINTENANCE));
        // 10*78 + 6.25*175 - 5*30 + 5 = 1728.75
        assertThat(s.bmrKcal()).isEqualTo(1729);
        assertThat(s.maintenanceKcal()).isEqualTo((int) Math.round(1728.75 * 1.55));
        assertThat(s.calorieTargetKcal()).isEqualTo(2680);
    }

    @Test
    void fatLossNeverGoesBelowRestingEnergy() {
        var s = calculator.suggest(profile(Sex.FEMALE, Goal.FAT_LOSS));
        assertThat(s.calorieTargetKcal()).isGreaterThanOrEqualTo(s.bmrKcal() - 10);
        assertThat(s.proteinTargetG()).isEqualByComparingTo("125"); // 1.6 g/kg
    }

    @Test
    void macrosAddUpToCalories() {
        var s = calculator.suggest(profile(Sex.MALE, Goal.MUSCLE_GAIN));
        int fromMacros = s.proteinTargetG().intValue() * 4 + s.carbsTargetG().intValue() * 4 + s.fatTargetG().intValue() * 9;
        assertThat(Math.abs(fromMacros - s.calorieTargetKcal())).isLessThan(15);
    }

    @Test
    void missingSexUsesMidpointAndSaysSo() {
        var s = calculator.suggest(profile(null, Goal.MAINTENANCE));
        assertThat(s.notes()).anyMatch(n -> n.contains("Sex not provided"));
    }

    @Test
    void requiresAgeHeightAndWeight() {
        UserProfile p = new UserProfile(1L, "Test");
        assertThatThrownBy(() -> calculator.suggest(p)).isInstanceOf(ApiException.class)
                .hasMessageContaining("age").hasMessageContaining("height");
    }
}
