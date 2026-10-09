package com.nutritrack.food;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** A household measure for a food, e.g. "1 medium idli" = 40 g, expressed in the food's basis unit. */
@Entity
@Table(name = "food_portions")
public class FoodPortion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(name = "unit_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal unitQuantity = BigDecimal.ONE;

    @Column(name = "basis_amount", nullable = false, precision = 10, scale = 3)
    private BigDecimal basisAmount;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPortion;

    @Column(name = "source_note")
    private String sourceNote;

    protected FoodPortion() {
    }

    public FoodPortion(Long foodId, String label, String unit, BigDecimal unitQuantity, BigDecimal basisAmount,
                       boolean defaultPortion, String sourceNote) {
        this.foodId = foodId;
        this.label = label;
        this.unit = unit;
        this.unitQuantity = unitQuantity;
        this.basisAmount = basisAmount;
        this.defaultPortion = defaultPortion;
        this.sourceNote = sourceNote;
    }

    public Long getId() { return id; }
    public Long getFoodId() { return foodId; }
    public String getLabel() { return label; }
    public String getUnit() { return unit; }
    public BigDecimal getUnitQuantity() { return unitQuantity; }
    public BigDecimal getBasisAmount() { return basisAmount; }
    public boolean isDefaultPortion() { return defaultPortion; }
    public String getSourceNote() { return sourceNote; }
}
