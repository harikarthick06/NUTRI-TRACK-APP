package com.nutritrack.food.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nutritrack.common.Enums.FoodType;
import com.nutritrack.config.AppProperties;
import com.nutritrack.nutrient.NutrientUnitConverter;
import com.nutritrack.testutil.TestNutrients;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenFoodFactsProviderTest {
    private MockRestServiceServer server;
    private OpenFoodFactsProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var props = new AppProperties(null, null, null, new AppProperties.Providers(null,
                new AppProperties.OpenFoodFacts(true, "https://off.test", "NutriTrack-test/1.0 (test@example.com)")));
        provider = new OpenFoodFactsProvider(props, builder, TestNutrients.catalog(), new NutrientUnitConverter());
    }

    @Test
    void fetchesProductByBarcodeWithUserAgentAndConvertsGramsToCanonicalUnits() throws IOException {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://off.test/api/v2/product/8901058000290.json")))
                .andExpect(header("User-Agent", "NutriTrack-test/1.0 (test@example.com)"))
                .andRespond(withSuccess(UsdaFdcProviderTest.fixture("off-product.json"), MediaType.APPLICATION_JSON));
        var food = provider.fetch("8901058000290").orElseThrow();
        assertThat(food.foodType()).isEqualTo(FoodType.BRANDED);
        assertThat(food.brand()).isEqualTo("Example brand");
        assertThat(food.category()).isEqualTo("Instant noodles");
        assertThat(food.nutrients().get("ENERGY_KCAL")).isEqualByComparingTo("427");
        assertThat(food.nutrients().get("SODIUM")).isEqualByComparingTo("1200");   // 1.2 g -> mg
        assertThat(food.nutrients().get("IRON")).isEqualByComparingTo("3.5");      // 0.0035 g -> mg
        assertThat(food.nutrients().get("FAT_TRANS")).isEqualByComparingTo("0");
        // Label carbohydrate excludes fibre in OFF data; vitamin A is skipped because labels do not state RAE
        assertThat(food.nutrients()).containsKey("AVAILABLE_CARBOHYDRATE").doesNotContainKey("VITAMIN_A_RAE");
        assertThat(food.portions()).singleElement().satisfies(p -> assertThat(p.basisAmount()).isEqualByComparingTo("70"));
        server.verify();
    }

    @Test
    void rejectsNonNumericBarcodesWithoutCallingTheApi() {
        assertThat(provider.fetch("12ab")).isEmpty();
        server.verify();
    }
}
