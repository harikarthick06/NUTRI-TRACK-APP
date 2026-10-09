package com.nutritrack.user;

import com.nutritrack.common.Enums.ActivityLevel;
import com.nutritrack.common.Enums.DietaryPreference;
import com.nutritrack.common.Enums.Goal;
import com.nutritrack.common.Enums.Sex;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class ProfileDtos {
    private ProfileDtos() {
    }

    public record ProfileResponse(
            long userId, String email, String name, Integer age, BigDecimal heightCm, BigDecimal currentWeightKg,
            BigDecimal targetWeightKg, Sex sex, ActivityLevel activityLevel, Integer workoutDaysPerWeek, Goal goal,
            String weightUnit, String heightUnit, String energyUnit, DietaryPreference dietaryPreference,
            String allergies, String dietaryRestrictions, Integer calorieTargetKcal, BigDecimal proteinTargetG,
            BigDecimal carbsTargetG, BigDecimal fatTargetG, BigDecimal fiberTargetG, Integer waterTargetMl,
            String referenceSystem, String timezone, boolean onboardingCompleted, BigDecimal bmi) {
    }

    /** Full replacement of the editable profile fields. Null clears an optional field. */
    public record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String name,
            @Min(14) @Max(120) Integer age,
            @DecimalMin("50.0") @DecimalMax("272.0") BigDecimal heightCm,
            @DecimalMin("20.0") @DecimalMax("400.0") BigDecimal currentWeightKg,
            @DecimalMin("20.0") @DecimalMax("400.0") BigDecimal targetWeightKg,
            Sex sex,
            ActivityLevel activityLevel,
            @Min(0) @Max(14) Integer workoutDaysPerWeek,
            Goal goal,
            DietaryPreference dietaryPreference,
            @Size(max = 1000) String allergies,
            @Size(max = 1000) String dietaryRestrictions,
            @Min(800) @Max(10000) Integer calorieTargetKcal,
            @DecimalMin("0") @DecimalMax("600") BigDecimal proteinTargetG,
            @DecimalMin("0") @DecimalMax("1500") BigDecimal carbsTargetG,
            @DecimalMin("0") @DecimalMax("600") BigDecimal fatTargetG,
            @DecimalMin("0") @DecimalMax("200") BigDecimal fiberTargetG,
            @Min(0) @Max(10000) Integer waterTargetMl,
            @Pattern(regexp = "NASEM_DRI") String referenceSystem,
            @Size(max = 64) String timezone,
            Boolean onboardingCompleted) {
    }

    public record TargetSuggestion(
            int calorieTargetKcal, BigDecimal proteinTargetG, BigDecimal carbsTargetG, BigDecimal fatTargetG,
            BigDecimal fiberTargetG, int waterTargetMl, int bmrKcal, int maintenanceKcal, List<String> notes) {
    }
}
