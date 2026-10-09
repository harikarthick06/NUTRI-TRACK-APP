package com.nutritrack.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutritrack.common.ApiException;
import com.nutritrack.common.Enums.BasisUnit;
import com.nutritrack.common.Enums.PreparationState;
import com.nutritrack.common.Enums.WeightState;
import com.nutritrack.food.Food;
import com.nutritrack.food.FoodPortion;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class QuantityResolverTest {
    private final QuantityResolver resolver = new QuantityResolver();

    private static Food food(String name, BasisUnit unit, PreparationState state, String yield) {
        Food f = new Food();
        f.setName(name);
        f.setBasisQuantity(new BigDecimal("100"));
        f.setBasisUnit(unit);
        f.setPreparationState(state);
        f.setCookedYieldFactor(yield == null ? null : new BigDecimal(yield));
        return f;
    }

    private static FoodPortion portion(long id, String label, String unit, String qty, String basis, boolean def) {
        FoodPortion p = new FoodPortion(1L, label, unit, new BigDecimal(qty), new BigDecimal(basis), def, null);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void massUnitsConvertToGrams() {
        Food rice = food("Rice", BasisUnit.G, PreparationState.RAW, "2.8");
        assertThat(resolver.resolve(rice, List.of(), bd("150"), "g", null, null).basisAmount()).isEqualByComparingTo("150");
        assertThat(resolver.resolve(rice, List.of(), bd("0.25"), "kg", null, null).basisAmount()).isEqualByComparingTo("250");
        assertThat(resolver.resolve(rice, List.of(), bd("1"), "oz", null, null).basisAmount()).isEqualByComparingTo("28.350");
    }

    @Test
    void cookedWeightOfRawFoodUsesYieldFactor() {
        Food rice = food("Rice, raw", BasisUnit.G, PreparationState.RAW, "2.8");
        var r = resolver.resolve(rice, List.of(), bd("280"), "g", null, WeightState.COOKED_WEIGHT_OF_RAW_FOOD);
        assertThat(r.basisAmount()).isEqualByComparingTo("100");
        assertThat(r.explanation()).contains("280 g cooked").contains("100 g raw").contains("estimated");
    }

    @Test
    void cookedWeightWithoutYieldIsRejectedNotGuessed() {
        Food dal = food("Some dal", BasisUnit.G, PreparationState.RAW, null);
        assertThatThrownBy(() -> resolver.resolve(dal, List.of(), bd("200"), "g", null, WeightState.COOKED_WEIGHT_OF_RAW_FOOD))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCode()).isEqualTo("NO_COOKED_YIELD");
                });
    }

    @Test
    void cookedWeightConversionRefusedForAlreadyCookedFood() {
        Food egg = food("Egg, boiled", BasisUnit.G, PreparationState.COOKED, null);
        assertThatThrownBy(() -> resolver.resolve(egg, List.of(), bd("50"), "g", null, WeightState.COOKED_WEIGHT_OF_RAW_FOOD))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_A_RAW_FOOD"));
    }

    @Test
    void cookedWeightMustBeAMass() {
        Food rice = food("Rice, raw", BasisUnit.G, PreparationState.RAW, "2.8");
        assertThatThrownBy(() -> resolver.resolve(rice, List.of(), bd("1"), "cup", null, WeightState.COOKED_WEIGHT_OF_RAW_FOOD))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("COOKED_WEIGHT_NEEDS_MASS"));
    }

    @Test
    void piecesUseThePortion() {
        Food egg = food("Egg", BasisUnit.G, PreparationState.RAW, null);
        List<FoodPortion> portions = List.of(portion(14, "1 large egg", "PIECE", "1", "50", true));
        assertThat(resolver.resolve(egg, portions, bd("2"), "piece", null, null).basisAmount()).isEqualByComparingTo("100");
        assertThat(resolver.resolve(egg, portions, bd("3"), "x", 14L, null).basisAmount()).isEqualByComparingTo("150");
    }

    @Test
    void pieceWithoutPortionIsRejected() {
        Food rice = food("Rice", BasisUnit.G, PreparationState.RAW, null);
        assertThatThrownBy(() -> resolver.resolve(rice, List.of(), bd("1"), "piece", null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NO_UNIT_CONVERSION"));
    }

    @Test
    void portionOfAnotherFoodIsRejected() {
        Food egg = food("Egg", BasisUnit.G, PreparationState.RAW, null);
        List<FoodPortion> portions = List.of(portion(14, "1 large egg", "PIECE", "1", "50", true));
        assertThatThrownBy(() -> resolver.resolve(egg, portions, bd("1"), "piece", 99L, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_PORTION"));
    }

    @Test
    void volumeOnMassFoodNeedsAHouseholdMeasure() {
        Food curd = food("Curd", BasisUnit.G, PreparationState.NOT_APPLICABLE, null);
        assertThatThrownBy(() -> resolver.resolve(curd, List.of(), bd("1"), "cup", null, null))
                .isInstanceOf(ApiException.class);
        // 1 cup = 245 g gives a density used for tbsp too: 1 tbsp = 15 mL x 245/240 g/mL.
        List<FoodPortion> portions = List.of(portion(1, "1 cup", "CUP", "1", "245", true));
        assertThat(resolver.resolve(curd, portions, bd("2"), "cup", null, null).basisAmount()).isEqualByComparingTo("490");
        assertThat(resolver.resolve(curd, portions, bd("1"), "tbsp", null, null).basisAmount()).isEqualByComparingTo("15.313");
    }

    @Test
    void volumeOnLiquidFoodIsDirect() {
        Food milk = food("Milk", BasisUnit.ML, PreparationState.NOT_APPLICABLE, null);
        assertThat(resolver.resolve(milk, List.of(), bd("1"), "cup", null, null).basisAmount()).isEqualByComparingTo("240");
        assertThat(resolver.resolve(milk, List.of(), bd("2"), "tsp", null, null).basisAmount()).isEqualByComparingTo("10");
    }

    @Test
    void zeroOrNegativeQuantityAndUnknownUnitAreRejected() {
        Food rice = food("Rice", BasisUnit.G, PreparationState.RAW, null);
        assertThatThrownBy(() -> resolver.resolve(rice, List.of(), bd("0"), "g", null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> resolver.resolve(rice, List.of(), bd("1"), "handful", null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("UNKNOWN_UNIT"));
    }

    @Test
    void supportedUnitsReflectBasisAndPortions() {
        Food egg = food("Egg", BasisUnit.G, PreparationState.RAW, null);
        assertThat(QuantityResolver.supportedUnits(egg, List.of(portion(1, "1 egg", "PIECE", "1", "50", true))))
                .contains("G", "PIECE").doesNotContain("CUP");
    }
}
