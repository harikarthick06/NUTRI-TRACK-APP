package com.nutritrack.diary;

import com.nutritrack.common.Enums.MealType;
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
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "food_diary_entries")
public class DiaryEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

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

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DiaryEntry() {
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getFoodId() { return foodId; }
    public void setFoodId(Long foodId) { this.foodId = foodId; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public Instant getLoggedAt() { return loggedAt; }
    public void setLoggedAt(Instant loggedAt) { this.loggedAt = loggedAt; }
    public MealType getMealType() { return mealType; }
    public void setMealType(MealType mealType) { this.mealType = mealType; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Long getPortionId() { return portionId; }
    public void setPortionId(Long portionId) { this.portionId = portionId; }
    public WeightState getWeightState() { return weightState; }
    public void setWeightState(WeightState weightState) { this.weightState = weightState; }
    public BigDecimal getBasisAmount() { return basisAmount; }
    public void setBasisAmount(BigDecimal basisAmount) { this.basisAmount = basisAmount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
