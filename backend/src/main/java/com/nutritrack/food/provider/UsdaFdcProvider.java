package com.nutritrack.food.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.nutritrack.common.Enums.BasisUnit;
import com.nutritrack.common.Enums.FoodType;
import com.nutritrack.config.AppProperties;
import com.nutritrack.food.FoodDataSource;
import com.nutritrack.food.provider.ProviderDtos.ExternalFoodSummary;
import com.nutritrack.food.provider.ProviderDtos.ImportedFood;
import com.nutritrack.food.provider.ProviderDtos.ImportedPortion;
import com.nutritrack.nutrient.NutrientCatalog;
import com.nutritrack.nutrient.NutrientCodes;
import com.nutritrack.nutrient.NutrientDefinition;
import com.nutritrack.nutrient.NutrientUnitConverter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * USDA FoodData Central (https://fdc.nal.usda.gov/api-guide). Uses GET /foods/search and GET /food/{fdcId}
 * with an api_key query parameter. FDC reports nutrients per 100 g (per 100 g or 100 mL for branded foods),
 * keyed by FDC nutrient id. Energy prefers id 1008 (kcal) and falls back to the Atwater energy ids 2048/2047
 * that Foundation foods use.
 */
@Component
public class UsdaFdcProvider implements FoodDataProvider {
    private static final Logger log = LoggerFactory.getLogger(UsdaFdcProvider.class);
    private static final int ENERGY_KCAL = 1008;
    private static final int ENERGY_ATWATER_SPECIFIC = 2048;
    private static final int ENERGY_ATWATER_GENERAL = 2047;
    private static final int SUGARS_TOTAL_NLEA = 2000;
    private static final int SUGARS_TOTAL_ALT = 1063;
    private static final int VITAMIN_D_IU = 1110;
    private static final String DATA_TYPES = "Foundation,SR Legacy,Survey (FNDDS),Branded";

    private final AppProperties.Usda config;
    private final RestClient client;
    private final NutrientCatalog catalog;
    private final NutrientUnitConverter converter;

    public UsdaFdcProvider(AppProperties props, RestClient.Builder builder, NutrientCatalog catalog,
                           NutrientUnitConverter converter) {
        this.config = props.providers().usda();
        this.client = builder.baseUrl(config.baseUrl()).build();
        this.catalog = catalog;
        this.converter = converter;
    }

    @Override
    public String sourceCode() {
        return FoodDataSource.USDA_FDC;
    }

    @Override
    public String displayName() {
        return "USDA FoodData Central";
    }

    @Override
    public boolean isEnabled() {
        return config.enabled() && config.apiKey() != null && !config.apiKey().isBlank();
    }

    @Override
    public String disabledReason() {
        if (!config.enabled()) {
            return "Set USDA_FDC_ENABLED=true to enable";
        }
        return isEnabled() ? null : "Set USDA_FDC_API_KEY to enable";
    }

    @Override
    public List<ExternalFoodSummary> search(String query, int limit) {
        JsonNode body = get(uri -> uri.path("/foods/search")
                .queryParam("query", query)
                .queryParam("pageSize", Math.min(limit, 50))
                .queryParam("dataType", DATA_TYPES)
                .queryParam("api_key", config.apiKey())
                .build());
        List<ExternalFoodSummary> out = new ArrayList<>();
        for (JsonNode f : body.path("foods")) {
            BigDecimal kcal = null;
            for (JsonNode n : f.path("foodNutrients")) {
                if (n.path("nutrientId").asInt() == ENERGY_KCAL && "KCAL".equalsIgnoreCase(n.path("unitName").asText())) {
                    kcal = n.path("value").decimalValue();
                }
            }
            String brand = textOrNull(f, "brandName", textOrNull(f, "brandOwner", null));
            out.add(new ExternalFoodSummary(sourceCode(), f.path("fdcId").asText(), f.path("description").asText(),
                    brand, f.path("dataType").asText(), kcal, BasisUnit.G));
        }
        return out;
    }

    @Override
    public Optional<ImportedFood> fetch(String externalId) {
        if (!externalId.matches("\\d{1,12}")) {
            return Optional.empty();
        }
        JsonNode f;
        try {
            f = get(uri -> uri.path("/food/{id}").queryParam("api_key", config.apiKey()).build(externalId));
        } catch (NotFound e) {
            return Optional.empty();
        }
        return Optional.of(map(f));
    }

    ImportedFood map(JsonNode f) {
        String description = f.path("description").asText();
        String dataType = f.path("dataType").asText();
        boolean branded = "Branded".equalsIgnoreCase(dataType);

        Map<Integer, JsonNode> byFdcId = new LinkedHashMap<>();
        for (JsonNode n : f.path("foodNutrients")) {
            JsonNode nutrient = n.path("nutrient");
            if (!n.has("amount") || n.path("amount").isNull()) {
                continue;
            }
            byFdcId.put(nutrient.path("id").asInt(), n);
        }
        Map<String, BigDecimal> nutrients = new LinkedHashMap<>();
        for (Map.Entry<Integer, JsonNode> e : byFdcId.entrySet()) {
            catalog.byUsdaId(e.getKey()).ifPresent(def -> put(nutrients, def, e.getValue()));
        }
        if (!nutrients.containsKey(NutrientCodes.ENERGY)) {
            for (int alt : new int[] {ENERGY_ATWATER_SPECIFIC, ENERGY_ATWATER_GENERAL}) {
                JsonNode n = byFdcId.get(alt);
                if (n != null) {
                    put(nutrients, catalog.byCode(NutrientCodes.ENERGY), n);
                    break;
                }
            }
        }
        if (!nutrients.containsKey(NutrientCodes.SUGARS_TOTAL) && byFdcId.containsKey(SUGARS_TOTAL_ALT)) {
            put(nutrients, catalog.byCode(NutrientCodes.SUGARS_TOTAL), byFdcId.get(SUGARS_TOTAL_ALT));
        }
        if (!nutrients.containsKey(NutrientCodes.VITAMIN_D) && byFdcId.containsKey(VITAMIN_D_IU)) {
            put(nutrients, catalog.byCode(NutrientCodes.VITAMIN_D), byFdcId.get(VITAMIN_D_IU));
        }

        List<ImportedPortion> portions = new ArrayList<>();
        for (JsonNode p : f.path("foodPortions")) {
            BigDecimal grams = p.path("gramWeight").decimalValue();
            if (grams.signum() <= 0) {
                continue;
            }
            BigDecimal amount = p.path("amount").isNumber() ? p.path("amount").decimalValue() : BigDecimal.ONE;
            String unitName = p.path("measureUnit").path("name").asText("");
            String modifier = p.path("modifier").asText("");
            String descriptionText = p.path("portionDescription").asText("");
            String label = !descriptionText.isBlank() && !"Quantity not specified".equals(descriptionText)
                    ? descriptionText
                    : (amount.stripTrailingZeros().toPlainString() + " " + (unitName.equals("undetermined") ? modifier
                    : (unitName + (modifier.isBlank() ? "" : ", " + modifier)))).trim();
            portions.add(new ImportedPortion(truncate(label, 100), householdUnit(unitName),
                    amount.signum() > 0 ? amount : BigDecimal.ONE, grams));
        }
        BasisUnit basisUnit = BasisUnit.G;
        if (branded) {
            String servingUnit = f.path("servingSizeUnit").asText("");
            if (servingUnit.equalsIgnoreCase("ml")) {
                basisUnit = BasisUnit.ML;
            }
            BigDecimal serving = f.path("servingSize").isNumber() ? f.path("servingSize").decimalValue() : null;
            if (serving != null && serving.signum() > 0) {
                String household = f.path("householdServingFullText").asText("");
                portions.add(new ImportedPortion(truncate(household.isBlank() ? "1 serving" : household, 100),
                        "SERVING", BigDecimal.ONE, serving));
            }
        }
        String category = branded ? textOrNull(f, "brandedFoodCategory", null)
                : f.path("foodCategory").path("description").asText(null);
        String brand = textOrNull(f, "brandName", textOrNull(f, "brandOwner", null));
        return new ImportedFood(sourceCode(), f.path("fdcId").asText(), description, brand, category,
                branded ? FoodType.BRANDED : FoodType.GENERIC, PreparationStateGuesser.guess(description),
                BigDecimal.valueOf(100), basisUnit, textOrNull(f, "gtinUpc", null), nutrients, portions,
                "FoodData Central " + dataType + " food " + f.path("fdcId").asText());
    }

    private void put(Map<String, BigDecimal> nutrients, NutrientDefinition def, JsonNode n) {
        BigDecimal amount = n.path("amount").decimalValue();
        String unit = n.path("nutrient").path("unitName").asText();
        converter.convert(amount, unit, def.getUnit(), def.getCode())
                .filter(v -> v.signum() >= 0)
                .ifPresent(v -> nutrients.putIfAbsent(def.getCode(), v));
    }

    private static String householdUnit(String measureUnitName) {
        String u = measureUnitName.toLowerCase();
        if (u.startsWith("cup")) return "CUP";
        if (u.startsWith("tablespoon") || u.equals("tbsp")) return "TBSP";
        if (u.startsWith("teaspoon") || u.equals("tsp")) return "TSP";
        if (u.startsWith("slice")) return "SLICE";
        return "PIECE";
    }

    private JsonNode get(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri) {
        if (!isEnabled()) {
            throw new ProviderException(displayName());
        }
        try {
            JsonNode body = client.get().uri(uri).retrieve()
                    .onStatus(s -> s.value() == 404, (req, res) -> {
                        throw new NotFound();
                    })
                    .body(JsonNode.class);
            if (body == null) {
                throw new ProviderException(displayName());
            }
            return body;
        } catch (NotFound e) {
            throw e;
        } catch (RestClientResponseException e) {
            // The URL contains the API key, so only the status is logged.
            log.warn("FoodData Central request failed with status {}", statusOf(e.getStatusCode()));
            throw new ProviderException(displayName());
        } catch (RestClientException e) {
            log.warn("FoodData Central request failed: {}", e.getClass().getSimpleName());
            throw new ProviderException(displayName());
        }
    }

    private static int statusOf(HttpStatusCode code) {
        return code.value();
    }

    private static String textOrNull(JsonNode node, String field, String fallback) {
        JsonNode v = node.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText().trim() : fallback;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    static class NotFound extends RuntimeException {
        NotFound() {
            super(null, null, false, false);
        }
    }
}
