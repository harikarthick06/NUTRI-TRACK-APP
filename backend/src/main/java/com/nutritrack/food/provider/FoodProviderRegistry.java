package com.nutritrack.food.provider;

import com.nutritrack.common.ApiException;
import com.nutritrack.food.provider.ProviderDtos.ProviderStatus;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class FoodProviderRegistry {
    private final List<FoodDataProvider> providers;

    public FoodProviderRegistry(List<FoodDataProvider> providers) {
        this.providers = providers;
    }

    public List<ProviderStatus> statuses() {
        return providers.stream()
                .map(p -> new ProviderStatus(p.sourceCode(), p.displayName(), p.isEnabled(), p.disabledReason()))
                .toList();
    }

    public FoodDataProvider enabled(String code) {
        String c = code == null ? "" : code.toUpperCase(Locale.ROOT);
        FoodDataProvider provider = providers.stream().filter(p -> p.sourceCode().equals(c)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_PROVIDER", "Unknown food data provider"));
        if (!provider.isEnabled()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_DISABLED",
                    provider.displayName() + " is not configured. " + provider.disabledReason() + ".");
        }
        return provider;
    }
}
