package com.nutritrack.meal;

import com.nutritrack.common.Enums.WeightState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "saved_meal_items")
public class SavedMealItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saved_meal_id", nullable = false)
    private Long savedMealId;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(name = "portion_id")
    private Long portionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_state", nullable = false, length = 32)
    private WeightState weightState = WeightState.AS_DEFINED;

    @Column(name = "basis_amount", nullable = false, precision = 12, scale = 3)
    private BigDecimal basisAmount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public SavedMealItem() {
    }

    public SavedMealItem(Long savedMealId, Long foodId, BigDecimal quantity, String unit, Long portionId,
                         WeightState weightState, BigDecimal basisAmount, int sortOrder) {
        this.savedMealId = savedMealId;
        this.foodId = foodId;
        this.quantity = quantity;
        this.unit = unit;
        this.portionId = portionId;
        this.weightState = weightState;
        this.basisAmount = basisAmount;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public Long getSavedMealId() { return savedMealId; }
    public Long getFoodId() { return foodId; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public Long getPortionId() { return portionId; }
    public WeightState getWeightState() { return weightState; }
    public BigDecimal getBasisAmount() { return basisAmount; }
    public int getSortOrder() { return sortOrder; }
}
