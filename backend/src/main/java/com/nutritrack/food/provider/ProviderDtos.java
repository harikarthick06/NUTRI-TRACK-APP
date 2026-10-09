package com.nutritrack.food.provider;

import com.nutritrack.common.Enums.BasisUnit;
import com.nutritrack.common.Enums.FoodType;
import com.nutritrack.common.Enums.PreparationState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ProviderDtos {
    private ProviderDtos() {
    }

    /** A search hit from an external provider, not yet stored locally. */
    public record ExternalFoodSummary(String provider, String externalId, String name, String brand, String dataType,
                                      BigDecimal energyKcalPerBasis, BasisUnit basisUnit) {
    }

    /** A food fetched from a provider, mapped to NutriTrack nutrient codes and canonical units per basis. */
    public record ImportedFood(String sourceCode, String externalId, String name, String brand, String category,
                               FoodType foodType, PreparationState preparationState, BigDecimal basisQuantity,
                               BasisUnit basisUnit, String barcode, Map<String, BigDecimal> nutrients,
                               List<ImportedPortion> portions, String notes) {
    }

    public record ImportedPortion(String label, String unit, BigDecimal unitQuantity, BigDecimal basisAmount) {
    }

    public record ProviderStatus(String code, String name, boolean enabled, String reason) {
    }
}
