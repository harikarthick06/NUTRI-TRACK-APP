package com.nutritrack.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "recipes")
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "system_key", length = 60)
    private String systemKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal servings = BigDecimal.ONE;

    @Column(name = "total_cooked_weight_g", precision = 10, scale = 2)
    private BigDecimal totalCookedWeightG;

    @Column(name = "serving_label", length = 100)
    private String servingLabel;

    @Column(name = "food_id")
    private Long foodId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Recipe() {
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public boolean isSystem() { return system; }
    public String getSystemKey() { return systemKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public BigDecimal getServings() { return servings; }
    public void setServings(BigDecimal servings) { this.servings = servings; }
    public BigDecimal getTotalCookedWeightG() { return totalCookedWeightG; }
    public void setTotalCookedWeightG(BigDecimal totalCookedWeightG) { this.totalCookedWeightG = totalCookedWeightG; }
    public String getServingLabel() { return servingLabel; }
    public void setServingLabel(String servingLabel) { this.servingLabel = servingLabel; }
    public Long getFoodId() { return foodId; }
    public void setFoodId(Long foodId) { this.foodId = foodId; }
    public Instant getUpdatedAt() { return updatedAt; }
}
