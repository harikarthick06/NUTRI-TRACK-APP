package com.nutritrack.target;

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

/** A user's own target and/or upper limit for one nutrient, replacing the reference value. */
@Entity
@Table(name = "user_nutrient_targets")
public class UserNutrientTarget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "nutrient_id", nullable = false)
    private Integer nutrientId;

    @Column(name = "target_amount", precision = 14, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "upper_limit", precision = 14, scale = 4)
    private BigDecimal upperLimit;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserNutrientTarget() {
    }

    public UserNutrientTarget(Long userId, Integer nutrientId) {
        this.userId = userId;
        this.nutrientId = nutrientId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Integer getNutrientId() { return nutrientId; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }
    public BigDecimal getUpperLimit() { return upperLimit; }
    public void setUpperLimit(BigDecimal upperLimit) { this.upperLimit = upperLimit; }
}
