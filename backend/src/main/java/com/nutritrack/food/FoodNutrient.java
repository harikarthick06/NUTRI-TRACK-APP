package com.nutritrack.food;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** Amount of one nutrient per food basis (e.g. per 100 g), in the nutrient's canonical unit. */
@Entity
@Table(name = "food_nutrients")
@IdClass(FoodNutrient.Key.class)
public class FoodNutrient {
    public static final String REPORTED = "REPORTED";
    public static final String DERIVED = "DERIVED";
    public static final String CALCULATED = "CALCULATED";
    public static final String USER_ENTERED = "USER_ENTERED";

    @Id
    @Column(name = "food_id")
    private Long foodId;

    @Id
    @Column(name = "nutrient_id")
    private Integer nutrientId;

    @Column(nullable = false, precision = 16, scale = 6)
    private BigDecimal amount;

    @Column(name = "value_origin", nullable = false, length = 20)
    private String valueOrigin = REPORTED;

    @Column(name = "coverage_pct", precision = 5, scale = 2)
    private BigDecimal coveragePct;

    protected FoodNutrient() {
    }

    public FoodNutrient(Long foodId, Integer nutrientId, BigDecimal amount, String valueOrigin, BigDecimal coveragePct) {
        this.foodId = foodId;
        this.nutrientId = nutrientId;
        this.amount = amount;
        this.valueOrigin = valueOrigin;
        this.coveragePct = coveragePct;
    }

    public Long getFoodId() { return foodId; }
    public Integer getNutrientId() { return nutrientId; }
    public BigDecimal getAmount() { return amount; }
    public String getValueOrigin() { return valueOrigin; }
    public BigDecimal getCoveragePct() { return coveragePct; }

    public static class Key implements Serializable {
        private Long foodId;
        private Integer nutrientId;

        public Key() {
        }

        public Key(Long foodId, Integer nutrientId) {
            this.foodId = foodId;
            this.nutrientId = nutrientId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(foodId, k.foodId) && Objects.equals(nutrientId, k.nutrientId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(foodId, nutrientId);
        }
    }
}
