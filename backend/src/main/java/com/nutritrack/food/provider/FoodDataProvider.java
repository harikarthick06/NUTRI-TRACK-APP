package com.nutritrack.food.provider;

import com.nutritrack.food.provider.ProviderDtos.ExternalFoodSummary;
import com.nutritrack.food.provider.ProviderDtos.ImportedFood;
import java.util.List;
import java.util.Optional;

/**
 * Abstraction over external food composition databases. Implementations map provider nutrients to
 * NutriTrack nutrient codes and canonical units, and must never invent values: a nutrient the provider
 * does not report is simply absent from {@link ImportedFood#nutrients()}.
 */
public interface FoodDataProvider {
    /** Matches a food_data_sources.code. */
    String sourceCode();

    String displayName();

    boolean isEnabled();

    /** Why the provider is disabled, for display; null when enabled. */
    String disabledReason();

    List<ExternalFoodSummary> search(String query, int limit);

    Optional<ImportedFood> fetch(String externalId);
}
