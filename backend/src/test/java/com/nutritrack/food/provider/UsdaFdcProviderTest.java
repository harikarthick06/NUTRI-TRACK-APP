package com.nutritrack.food.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nutritrack.common.Enums.FoodType;
import com.nutritrack.common.Enums.PreparationState;
import com.nutritrack.config.AppProperties;
import com.nutritrack.nutrient.NutrientUnitConverter;
import com.nutritrack.testutil.TestNutrients;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UsdaFdcProviderTest {
    private MockRestServiceServer server;
    private UsdaFdcProvider provider;

    static String fixture(String name) throws IOException {
        return new ClassPathResource("fixtures/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    private static AppProperties props(boolean enabled, String key) {
        return new AppProperties(null, null, null, new AppProperties.Providers(
                new AppProperties.Usda(enabled, key, "https://fdc.test/fdc/v1"), null));
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new UsdaFdcProvider(props(true, "TEST_KEY"), builder, TestNutrients.catalog(), new NutrientUnitConverter());
    }

    @Test
    void searchMapsHitsAndSendsApiKey() throws IOException {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://fdc.test/fdc/v1/foods/search")))
                .andExpect(queryParam("api_key", "TEST_KEY"))
                .andExpect(queryParam("query", "rice"))
                .andRespond(withSuccess(fixture("fdc-search.json"), MediaType.APPLICATION_JSON));
        var hits = provider.search("rice", 10);
        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).externalId()).isEqualTo("168878");
        assertThat(hits.get(0).energyKcalPerBasis()).isEqualByComparingTo("130");
        assertThat(hits.get(1).brand()).isEqualTo("EXAMPLE");
        assertThat(hits.get(1).energyKcalPerBasis()).isNull();
        server.verify();
    }

    @Test
    void fetchConvertsUnitsAndSkipsUnconvertibleOrMissingValues() throws IOException {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://fdc.test/fdc/v1/food/168878")))
                .andRespond(withSuccess(fixture("fdc-food-sr-legacy.json"), MediaType.APPLICATION_JSON));
        var food = provider.fetch("168878").orElseThrow();
        assertThat(food.nutrients().get("ENERGY_KCAL")).isEqualByComparingTo("130");
        assertThat(food.nutrients().get("PROTEIN")).isEqualByComparingTo("2.69");
        // Fluoride is reported in micrograms by FDC and stored in milligrams
        assertThat(food.nutrients().get("FLUORIDE")).isEqualByComparingTo("0.0375");
        // Vitamin D in IU converts (0 IU = 0 ug); vitamin A IU cannot be converted to RAE and is skipped
        assertThat(food.nutrients().get("VITAMIN_D")).isEqualByComparingTo("0");
        assertThat(food.nutrients()).doesNotContainKey("VITAMIN_A_RAE");
        // Selenium has no amount, so it stays unknown rather than becoming zero
        assertThat(food.nutrients()).doesNotContainKey("SELENIUM");
        assertThat(food.preparationState()).isEqualTo(PreparationState.COOKED);
        assertThat(food.foodType()).isEqualTo(FoodType.GENERIC);
        assertThat(food.portions()).singleElement().satisfies(p -> {
            assertThat(p.basisAmount()).isEqualByComparingTo("158");
            assertThat(p.label()).isEqualTo("1 cup");
        });
    }

    @Test
    void foundationFoodsFallBackToAtwaterEnergy() throws IOException {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://fdc.test/fdc/v1/food/2346393")))
                .andRespond(withSuccess(fixture("fdc-food-foundation.json"), MediaType.APPLICATION_JSON));
        var food = provider.fetch("2346393").orElseThrow();
        assertThat(food.nutrients().get("ENERGY_KCAL")).isEqualByComparingTo("115");
        assertThat(food.nutrients().get("SUGARS_TOTAL")).isEqualByComparingTo("0");
        assertThat(food.preparationState()).isEqualTo(PreparationState.RAW);
        assertThat(food.portions().get(0).label()).isEqualTo("1 breast");
    }

    @Test
    void unknownIdIsEmpty() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://fdc.test/fdc/v1/food/1")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(provider.fetch("1")).isEmpty();
        assertThat(provider.fetch("abc")).isEmpty();
        server.verify();
    }

    @Test
    void serverErrorsAreReportedWithoutLeakingTheKey() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://fdc.test/fdc/v1/foods/search")))
                .andRespond(withServerError());
        assertThatThrownBy(() -> provider.search("rice", 5)).isInstanceOf(ProviderException.class)
                .hasMessageNotContaining("TEST_KEY");
    }

    @Test
    void disabledWithoutApiKey() {
        var p = new UsdaFdcProvider(props(true, ""), RestClient.builder(), TestNutrients.catalog(), new NutrientUnitConverter());
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.disabledReason()).contains("USDA_FDC_API_KEY");
    }
}
