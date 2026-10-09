package com.nutritrack.diary;

import com.nutritrack.common.Enums.BasisUnit;
import com.nutritrack.common.Enums.DataQuality;
import com.nutritrack.common.Enums.MealType;
import com.nutritrack.common.Enums.PreparationState;
import com.nutritrack.common.Enums.WeightState;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DiaryDtos {
    private DiaryDtos() {
    }

    public record EntryRequest(
            @NotNull Long foodId,
            @NotNull LocalDate date,
            @NotNull MealType mealType,
            @NotNull @DecimalMin(value = "0.001") @DecimalMax("100000") BigDecimal quantity,
            @NotBlank @Size(max = 16) String unit,
            Long portionId,
            WeightState weightState,
            Instant loggedAt,
            @Size(max = 500) String notes) {
    }

    public record EntryUpdateRequest(
            @NotNull @DecimalMin(value = "0.001") @DecimalMax("100000") BigDecimal quantity,
            @NotBlank @Size(max = 16) String unit,
            Long portionId,
            WeightState weightState,
            MealType mealType,
            LocalDate date,
            Instant loggedAt,
            @Size(max = 500) String notes) {
    }

    /** A macro total with how many of the contributing entries had data for it. */
    public record MacroTotal(BigDecimal amount, int entriesWithData, int entries) {
    }

    public record Macros(MacroTotal energyKcal, MacroTotal proteinG, MacroTotal carbsG, MacroTotal fatG,
                         MacroTotal fiberG) {
    }

    public record EntryDto(long id, long foodId, String foodName, String brand, DataQuality dataQuality,
                           BigDecimal foodCompletenessPct, PreparationState preparationState, MealType mealType,
                           LocalDate date, Instant loggedAt, BigDecimal quantity, String unit, Long portionId,
                           String portionLabel, WeightState weightState, BigDecimal basisAmount, BasisUnit basisUnit,
                           String notes, BigDecimal energyKcal, BigDecimal proteinG, BigDecimal carbsG,
                           BigDecimal fatG, BigDecimal fiberG, List<String> unknownMacros) {
    }

    public record MealDto(MealType mealType, List<EntryDto> entries, Macros totals) {
    }

    public record DayDto(LocalDate date, List<MealDto> meals, Macros totals, int entryCount, boolean markedComplete,
                         String dayNote) {
    }

    public record CopyDayRequest(@NotNull LocalDate fromDate, @NotNull LocalDate toDate, List<MealType> mealTypes) {
    }

    public record CopyMealRequest(@NotNull LocalDate fromDate, @NotNull MealType fromMeal, @NotNull LocalDate toDate,
                                  @NotNull MealType toMeal) {
    }

    public record CopyResult(int copiedEntries, LocalDate toDate) {
    }

    public record DayStatusRequest(@NotNull Boolean completed, @Size(max = 500) String note) {
    }
}
