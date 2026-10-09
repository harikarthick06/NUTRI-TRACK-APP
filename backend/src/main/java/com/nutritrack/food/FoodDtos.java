package com.nutritrack.food;

import com.nutritrack.common.Enums.BasisUnit;
import com.nutritrack.common.Enums.DataQuality;
import com.nutritrack.common.Enums.FoodType;
import com.nutritrack.common.Enums.PreparationState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class FoodDtos {
    private FoodDtos() {
    }

    public record PortionDto(Long id, String label, String unit, BigDecimal unitQuantity, BigDecimal basisAmount,
                             boolean isDefault, String sourceNote) {
    }

    public record SourceDto(String code, String name, String url, String version, String licenseNote) {
    }

    public record FoodSummary(long id, String name, String brand, String category, FoodType foodType,
                              PreparationState preparationState, BigDecimal basisQuantity, BasisUnit basisUnit,
                              DataQuality dataQuality, String sourceCode, BigDecimal completenessPct,
                              BigDecimal energyKcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG,
                              PortionDto defaultPortion, boolean favorite, boolean owned) {
    }

    /** One nutrient row in a food detail. amount is null when the value is unknown (never zero-filled). */
    public record FoodNutrientDto(String code, String name, String unit, String category, String formNote,
                                  BigDecimal amount, boolean known, String valueOrigin, BigDecimal coveragePct) {
    }

    public record EnergyCheck(BigDecimal reportedKcal, BigDecimal estimatedKcal, BigDecimal differencePct,
                              String status, String note) {
    }

    public record FoodDetail(long id, String name, String brand, String category, FoodType foodType,
                             PreparationState preparationState, BigDecimal basisQuantity, BasisUnit basisUnit,
                             DataQuality dataQuality, SourceDto source, String sourceFoodRef,
                             BigDecimal completenessPct, String localNames, String barcode,
                             BigDecimal ediblePortionPct, BigDecimal cookedYieldFactor, String cookedYieldNote,
                             String notes, Long recipeId, List<FoodNutrientDto> nutrients, List<PortionDto> portions,
                             EnergyCheck energyCheck, boolean favorite, boolean owned) {
    }

    public record PortionInput(
            @NotBlank @Size(max = 100) String label,
            @NotBlank @Pattern(regexp = "PIECE|CUP|TBSP|TSP|SERVING|SLICE|BOWL") String unit,
            @NotNull @DecimalMin(value = "0.001") @DecimalMax("10000") BigDecimal unitQuantity,
            @NotNull @DecimalMin(value = "0.001") @DecimalMax("100000") BigDecimal basisAmount) {
    }

    /**
     * Custom food. Nutrient amounts are per basisQuantity of basisUnit, in each nutrient's canonical unit
     * (see GET /api/nutrients). Omit a nutrient when it is unknown; do not send 0 unless it is truly zero.
     */
    public record CustomFoodRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String brand,
            @Size(max = 100) String category,
            PreparationState preparationState,
            @NotNull @DecimalMin(value = "0.001") @DecimalMax("100000") BigDecimal basisQuantity,
            @NotNull BasisUnit basisUnit,
            @NotNull Map<@NotBlank String, @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal> nutrients,
            @Valid @Size(max = 20) List<PortionInput> portions,
            @Size(max = 32) String barcode,
            @Size(max = 1000) String notes) {
    }

    public record ImportRequest(
            @NotBlank @Pattern(regexp = "USDA_FDC|OPEN_FOOD_FACTS") String provider,
            @NotBlank @Size(max = 64) String externalId) {
    }
}
