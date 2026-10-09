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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Open Food Facts (https://openfoodfacts.github.io/openfoodfacts-server/api/) for packaged foods.
 * Product lookup: GET /api/v2/product/{barcode}.json. Text search: GET /cgi/search.pl?search_terms=...&json=1.
 * The API needs no key but asks for a descriptive User-Agent, and limits search to about 10 requests per
 * minute. Data is ODbL licensed and crowd-sourced from labels, so imported foods are marked EXTERNAL.
 * The "_100g" nutriment values are per 100 g, or per 100 mL for liquids, and mass nutrients are in grams.
 */
@Component
public class OpenFoodFactsProvider implements FoodDataProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsProvider.class);
    private static final String FIELDS = "code,product_name,product_name_en,brands,categories,nutriments,"
            + "serving_size,serving_quantity,product_quantity_unit,quantity";

    /** Open Food Facts nutriment key (without the _100g suffix) to NutriTrack nutrient code. */
    static final Map<String, String> NUTRIMENTS = new LinkedHashMap<>();

    static {
        NUTRIMENTS.put("energy-kcal", NutrientCodes.ENERGY);
        NUTRIMENTS.put("proteins", "PROTEIN");
        NUTRIMENTS.put("carbohydrates", "AVAILABLE_CARBOHYDRATE"); // EU labels exclude fibre
        NUTRIMENTS.put("fat", "FAT_TOTAL");
        NUTRIMENTS.put("saturated-fat", "FAT_SATURATED");
        NUTRIMENTS.put("monounsaturated-fat", "FAT_MONOUNSATURATED");
        NUTRIMENTS.put("polyunsaturated-fat", "FAT_POLYUNSATURATED");
        NUTRIMENTS.put("trans-fat", "FAT_TRANS");
        NUTRIMENTS.put("cholesterol", "CHOLESTEROL");
        NUTRIMENTS.put("fiber", "FIBER");
        NUTRIMENTS.put("sugars", "SUGARS_TOTAL");
        NUTRIMENTS.put("added-sugars", "SUGARS_ADDED");
        NUTRIMENTS.put("omega-3-fat", "OMEGA3_TOTAL");
        NUTRIMENTS.put("omega-6-fat", "OMEGA6_TOTAL");
        NUTRIMENTS.put("alpha-linolenic-acid", "ALA");
        NUTRIMENTS.put("linoleic-acid", "LINOLEIC_ACID");
        NUTRIMENTS.put("eicosapentaenoic-acid", "EPA");
        NUTRIMENTS.put("docosahexaenoic-acid", "DHA");
        NUTRIMENTS.put("vitamin-c", "VITAMIN_C");
        NUTRIMENTS.put("vitamin-d", "VITAMIN_D");
        NUTRIMENTS.put("vitamin-e", "VITAMIN_E");
        NUTRIMENTS.put("vitamin-k", "VITAMIN_K");
        NUTRIMENTS.put("vitamin-b1", "THIAMIN");
        NUTRIMENTS.put("vitamin-b2", "RIBOFLAVIN");
        NUTRIMENTS.put("vitamin-pp", "NIACIN");
        NUTRIMENTS.put("pantothenic-acid", "PANTOTHENIC_ACID");
        NUTRIMENTS.put("vitamin-b6", "VITAMIN_B6");
        NUTRIMENTS.put("biotin", "BIOTIN");
        NUTRIMENTS.put("vitamin-b12", "VITAMIN_B12");
        NUTRIMENTS.put("calcium", "CALCIUM");
        NUTRIMENTS.put("iron", "IRON");
        NUTRIMENTS.put("magnesium", "MAGNESIUM");
        NUTRIMENTS.put("phosphorus", "PHOSPHORUS");
        NUTRIMENTS.put("potassium", "POTASSIUM");
        NUTRIMENTS.put("sodium", "SODIUM");
        NUTRIMENTS.put("zinc", "ZINC");
        NUTRIMENTS.put("copper", "COPPER");
        NUTRIMENTS.put("manganese", "MANGANESE");
        NUTRIMENTS.put("selenium", "SELENIUM");
        NUTRIMENTS.put("iodine", "IODINE");
        NUTRIMENTS.put("chromium", "CHROMIUM");
        NUTRIMENTS.put("molybdenum", "MOLYBDENUM");
        NUTRIMENTS.put("chloride", "CHLORIDE");
        NUTRIMENTS.put("fluoride", "FLUORIDE");
        NUTRIMENTS.put("choline", "CHOLINE");
        // vitamin-a and vitamin-b9 are skipped: labels do not say whether they are RAE / DFE.
    }

    private final AppProperties.OpenFoodFacts config;
    private final RestClient client;
    private final NutrientCatalog catalog;
    private final NutrientUnitConverter converter;

    public OpenFoodFactsProvider(AppProperties props, RestClient.Builder builder, NutrientCatalog catalog,
                                 NutrientUnitConverter converter) {
        this.config = props.providers().openfoodfacts();
        this.client = builder.baseUrl(config.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, config.userAgent())
                .build();
        this.catalog = catalog;
        this.converter = converter;
    }

    @Override
    public String sourceCode() {
        return FoodDataSource.OPEN_FOOD_FACTS;
    }

    @Override
    public String displayName() {
        return "Open Food Facts";
    }

    @Override
    public boolean isEnabled() {
        return config.enabled();
    }

    @Override
    public String disabledReason() {
        return isEnabled() ? null : "Set OPENFOODFACTS_ENABLED=true to enable";
    }

    @Override
    public List<ExternalFoodSummary> search(String query, int limit) {
        JsonNode body = get(uri -> uri.path("/cgi/search.pl")
                .queryParam("search_terms", query)
                .queryParam("search_simple", 1)
                .queryParam("action", "process")
                .queryParam("json", 1)
                .queryParam("page_size", Math.min(limit, 50))
                .queryParam("fields", FIELDS)
                .build());
        List<ExternalFoodSummary> out = new ArrayList<>();
        for (JsonNode p : body.path("products")) {
            String code = p.path("code").asText("");
            String name = productName(p);
            if (code.isBlank() || name == null) {
                continue;
            }
            JsonNode kcal = p.path("nutriments").path("energy-kcal_100g");
            out.add(new ExternalFoodSummary(sourceCode(), code, name, brand(p), "Packaged product",
                    kcal.isNumber() ? kcal.decimalValue() : null, basisUnit(p)));
        }
        return out;
    }

    @Override
    public Optional<ImportedFood> fetch(String barcode) {
        if (!barcode.matches("\\d{4,14}")) {
            return Optional.empty();
        }
        JsonNode body = get(uri -> uri.path("/api/v2/product/{code}.json").queryParam("fields", FIELDS).build(barcode));
        if (body.path("status").asInt(0) != 1 || !body.has("product")) {
            return Optional.empty();
        }
        return Optional.of(map(body.path("product"), barcode));
    }

    ImportedFood map(JsonNode p, String barcode) {
        JsonNode nutriments = p.path("nutriments");
        Map<String, BigDecimal> nutrients = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : NUTRIMENTS.entrySet()) {
            JsonNode v = nutriments.path(e.getKey() + "_100g");
            if (!v.isNumber()) {
                continue;
            }
            NutrientDefinition def = catalog.byCode(e.getValue());
            // OFF normalises _100g values: kcal for energy-kcal, grams for every mass nutrient.
            String fromUnit = e.getKey().equals("energy-kcal") ? "KCAL" : "G";
            converter.convert(v.decimalValue(), fromUnit, def.getUnit(), def.getCode())
                    .filter(x -> x.signum() >= 0)
                    .ifPresent(x -> nutrients.put(def.getCode(), x));
        }
        List<ImportedPortion> portions = new ArrayList<>();
        JsonNode servingQty = p.path("serving_quantity");
        BigDecimal serving = servingQty.isNumber() ? servingQty.decimalValue()
                : servingQty.isTextual() ? parse(servingQty.asText()) : null;
        if (serving != null && serving.signum() > 0) {
            String label = p.path("serving_size").asText("1 serving");
            portions.add(new ImportedPortion(label.length() > 100 ? label.substring(0, 100) : label, "SERVING",
                    BigDecimal.ONE, serving));
        }
        String category = null;
        String categories = p.path("categories").asText("");
        if (!categories.isBlank()) {
            String[] parts = categories.split(",");
            category = parts[parts.length - 1].trim();
            if (category.length() > 100) {
                category = category.substring(0, 100);
            }
        }
        String name = productName(p);
        return new ImportedFood(sourceCode(), barcode, name == null ? "Product " + barcode : name, brand(p), category,
                FoodType.BRANDED, PreparationStateGuesser.guess(name), BigDecimal.valueOf(100), basisUnit(p), barcode,
                nutrients, portions, "Open Food Facts product " + barcode + " (label data, ODbL)");
    }

    private static BasisUnit basisUnit(JsonNode p) {
        String unit = p.path("product_quantity_unit").asText("").toLowerCase(Locale.ROOT);
        return unit.equals("ml") || unit.equals("l") || unit.equals("cl") ? BasisUnit.ML : BasisUnit.G;
    }

    private static String productName(JsonNode p) {
        for (String field : new String[] {"product_name", "product_name_en"}) {
            String v = p.path(field).asText("").trim();
            if (!v.isEmpty()) {
                return v.length() > 255 ? v.substring(0, 255) : v;
            }
        }
        return null;
    }

    private static String brand(JsonNode p) {
        String brands = p.path("brands").asText("").trim();
        if (brands.isEmpty()) {
            return null;
        }
        String first = brands.split(",")[0].trim();
        return first.length() > 255 ? first.substring(0, 255) : first;
    }

    private static BigDecimal parse(String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JsonNode get(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri) {
        if (!isEnabled()) {
            throw new ProviderException(displayName());
        }
        try {
            JsonNode body = client.get().uri(uri).retrieve().body(JsonNode.class);
            if (body == null) {
                throw new ProviderException(displayName());
            }
            return body;
        } catch (RestClientException e) {
            log.warn("Open Food Facts request failed: {}", e.getClass().getSimpleName());
            throw new ProviderException(displayName());
        }
    }
}
