package com.nutritrack.food;

import com.nutritrack.common.Enums.BasisUnit;
import com.nutritrack.common.Enums.DataQuality;
import com.nutritrack.common.Enums.FoodType;
import com.nutritrack.common.Enums.PreparationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "foods")
public class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String brand;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_type", nullable = false, length = 20)
    private FoodType foodType;

    @Enumerated(EnumType.STRING)
    @Column(name = "preparation_state", nullable = false, length = 20)
    private PreparationState preparationState = PreparationState.NOT_APPLICABLE;

    @Column(name = "basis_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal basisQuantity = BigDecimal.valueOf(100);

    @Enumerated(EnumType.STRING)
    @Column(name = "basis_unit", nullable = false, length = 4)
    private BasisUnit basisUnit = BasisUnit.G;

    @Column(name = "source_id", nullable = false)
    private Integer sourceId;

    @Column(name = "source_food_ref", length = 64)
    private String sourceFoodRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_quality", nullable = false, length = 20)
    private DataQuality dataQuality;

    @Column(name = "completeness_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal completenessPct = BigDecimal.ZERO;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "recipe_id")
    private Long recipeId;

    @Column(name = "local_names", length = 1000)
    private String localNames;

    @Column(length = 32)
    private String barcode;

    @Column(name = "edible_portion_pct", precision = 5, scale = 2)
    private BigDecimal ediblePortionPct;

    @Column(name = "cooked_yield_factor", precision = 6, scale = 3)
    private BigDecimal cookedYieldFactor;

    @Column(name = "cooked_yield_note")
    private String cookedYieldNote;

    @Column(length = 1000)
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Food() {
    }

    public boolean isVisibleTo(long userId) {
        return active && (ownerUserId == null || ownerUserId == userId);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public FoodType getFoodType() { return foodType; }
    public void setFoodType(FoodType foodType) { this.foodType = foodType; }
    public PreparationState getPreparationState() { return preparationState; }
    public void setPreparationState(PreparationState preparationState) { this.preparationState = preparationState; }
    public BigDecimal getBasisQuantity() { return basisQuantity; }
    public void setBasisQuantity(BigDecimal basisQuantity) { this.basisQuantity = basisQuantity; }
    public BasisUnit getBasisUnit() { return basisUnit; }
    public void setBasisUnit(BasisUnit basisUnit) { this.basisUnit = basisUnit; }
    public Integer getSourceId() { return sourceId; }
    public void setSourceId(Integer sourceId) { this.sourceId = sourceId; }
    public String getSourceFoodRef() { return sourceFoodRef; }
    public void setSourceFoodRef(String sourceFoodRef) { this.sourceFoodRef = sourceFoodRef; }
    public DataQuality getDataQuality() { return dataQuality; }
    public void setDataQuality(DataQuality dataQuality) { this.dataQuality = dataQuality; }
    public BigDecimal getCompletenessPct() { return completenessPct; }
    public void setCompletenessPct(BigDecimal completenessPct) { this.completenessPct = completenessPct; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }
    public String getLocalNames() { return localNames; }
    public void setLocalNames(String localNames) { this.localNames = localNames; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public BigDecimal getEdiblePortionPct() { return ediblePortionPct; }
    public void setEdiblePortionPct(BigDecimal ediblePortionPct) { this.ediblePortionPct = ediblePortionPct; }
    public BigDecimal getCookedYieldFactor() { return cookedYieldFactor; }
    public void setCookedYieldFactor(BigDecimal cookedYieldFactor) { this.cookedYieldFactor = cookedYieldFactor; }
    public String getCookedYieldNote() { return cookedYieldNote; }
    public void setCookedYieldNote(String cookedYieldNote) { this.cookedYieldNote = cookedYieldNote; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getUpdatedAt() { return updatedAt; }
}
