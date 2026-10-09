package com.nutritrack.recipe;

import com.nutritrack.common.Enums.DataQuality;
import com.nutritrack.common.Enums.MealType;
import com.nutritrack.common.Enums.WeightState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class RecipeDtos {
    private RecipeDtos() {
    }

    public record IngredientInput(
            @NotNull Long foodId,
            @NotNull @DecimalMin(value = "0.001") @DecimalMax("100000") BigDecimal quantity,
            @NotBlank @Size(max = 16) String unit,
            Long portionId,
            WeightState weightState,
            @Size(max = 255) String note) {
    }

    public record RecipeRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @Size(max = 10000) String instructions,
            @NotNull @DecimalMin(value = "0.1") @DecimalMax("1000") BigDecimal servings,
            @DecimalMin(value = "1") @DecimalMax("100000") BigDecimal totalCookedWeightG,
            @Size(max = 100) String servingLabel,
            @NotEmpty @Size(max = 100) List<@Valid IngredientInput> ingredients) {
    }

    public record IngredientDto(Long id, long foodId, String foodName, DataQuality dataQuality, BigDecimal quantity,
                                String unit, Long portionId, WeightState weightState, BigDecimal basisAmount,
                                BigDecimal grams, BigDecimal energyKcal, String note) {
    }

    public record RecipeNutrientDto(String code, String name, String unit, String category, BigDecimal per100g,
                                    BigDecimal perServing, BigDecimal total, BigDecimal coveragePct) {
    }

    public record RecipeNutrition(BigDecimal ingredientWeightG, BigDecimal cookedWeightG, BigDecimal servings,
                                  BigDecimal servingWeightG, BigDecimal completenessPct,
                                  List<RecipeNutrientDto> nutrients, List<String> missingNutrients,
                                  List<String> warnings) {
    }

    public record RecipeSummary(long id, String name, String description, BigDecimal servings,
                                BigDecimal servingWeightG, String servingLabel, BigDecimal energyKcalPerServing,
                                BigDecimal proteinGPerServing, Long foodId, boolean system, boolean owned,
                                int ingredientCount, Instant updatedAt) {
    }

    public record RecipeDetail(long id, String name, String description, String instructions, BigDecimal servings,
                               BigDecimal totalCookedWeightG, String servingLabel, Long foodId, boolean system,
                               boolean owned, List<IngredientDto> ingredients, RecipeNutrition nutrition) {
    }

    public record LogRecipeRequest(
            @NotNull LocalDate date,
            @NotNull MealType mealType,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("1000") BigDecimal servings,
            Boolean wholeRecipe) {
    }
}
