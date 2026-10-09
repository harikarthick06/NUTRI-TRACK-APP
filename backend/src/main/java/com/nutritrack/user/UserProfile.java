package com.nutritrack.user;

import com.nutritrack.common.Enums.ActivityLevel;
import com.nutritrack.common.Enums.DietaryPreference;
import com.nutritrack.common.Enums.Goal;
import com.nutritrack.common.Enums.Sex;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "smallint")
    private Integer age;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "current_weight_kg", precision = 5, scale = 2)
    private BigDecimal currentWeightKg;

    @Column(name = "target_weight_kg", precision = 5, scale = 2)
    private BigDecimal targetWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 20)
    private ActivityLevel activityLevel;

    @Column(name = "workout_days_per_week", columnDefinition = "tinyint")
    private Integer workoutDaysPerWeek;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Goal goal;

    @Column(name = "weight_unit", nullable = false, length = 4)
    private String weightUnit = "KG";

    @Column(name = "height_unit", nullable = false, length = 4)
    private String heightUnit = "CM";

    @Column(name = "energy_unit", nullable = false, length = 4)
    private String energyUnit = "KCAL";

    @Enumerated(EnumType.STRING)
    @Column(name = "dietary_preference", length = 20)
    private DietaryPreference dietaryPreference;

    @Column(length = 1000)
    private String allergies;

    @Column(name = "dietary_restrictions", length = 1000)
    private String dietaryRestrictions;

    @Column(name = "calorie_target_kcal")
    private Integer calorieTargetKcal;

    @Column(name = "protein_target_g", precision = 7, scale = 2)
    private BigDecimal proteinTargetG;

    @Column(name = "carbs_target_g", precision = 7, scale = 2)
    private BigDecimal carbsTargetG;

    @Column(name = "fat_target_g", precision = 7, scale = 2)
    private BigDecimal fatTargetG;

    @Column(name = "fiber_target_g", precision = 7, scale = 2)
    private BigDecimal fiberTargetG;

    @Column(name = "water_target_ml")
    private Integer waterTargetMl;

    @Column(name = "reference_system", nullable = false, length = 30)
    private String referenceSystem = "NASEM_DRI";

    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    public UserProfile(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
    public BigDecimal getCurrentWeightKg() { return currentWeightKg; }
    public void setCurrentWeightKg(BigDecimal currentWeightKg) { this.currentWeightKg = currentWeightKg; }
    public BigDecimal getTargetWeightKg() { return targetWeightKg; }
    public void setTargetWeightKg(BigDecimal targetWeightKg) { this.targetWeightKg = targetWeightKg; }
    public Sex getSex() { return sex; }
    public void setSex(Sex sex) { this.sex = sex; }
    public ActivityLevel getActivityLevel() { return activityLevel; }
    public void setActivityLevel(ActivityLevel activityLevel) { this.activityLevel = activityLevel; }
    public Integer getWorkoutDaysPerWeek() { return workoutDaysPerWeek; }
    public void setWorkoutDaysPerWeek(Integer workoutDaysPerWeek) { this.workoutDaysPerWeek = workoutDaysPerWeek; }
    public Goal getGoal() { return goal; }
    public void setGoal(Goal goal) { this.goal = goal; }
    public String getWeightUnit() { return weightUnit; }
    public String getHeightUnit() { return heightUnit; }
    public String getEnergyUnit() { return energyUnit; }
    public DietaryPreference getDietaryPreference() { return dietaryPreference; }
    public void setDietaryPreference(DietaryPreference dietaryPreference) { this.dietaryPreference = dietaryPreference; }
    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }
    public String getDietaryRestrictions() { return dietaryRestrictions; }
    public void setDietaryRestrictions(String dietaryRestrictions) { this.dietaryRestrictions = dietaryRestrictions; }
    public Integer getCalorieTargetKcal() { return calorieTargetKcal; }
    public void setCalorieTargetKcal(Integer calorieTargetKcal) { this.calorieTargetKcal = calorieTargetKcal; }
    public BigDecimal getProteinTargetG() { return proteinTargetG; }
    public void setProteinTargetG(BigDecimal proteinTargetG) { this.proteinTargetG = proteinTargetG; }
    public BigDecimal getCarbsTargetG() { return carbsTargetG; }
    public void setCarbsTargetG(BigDecimal carbsTargetG) { this.carbsTargetG = carbsTargetG; }
    public BigDecimal getFatTargetG() { return fatTargetG; }
    public void setFatTargetG(BigDecimal fatTargetG) { this.fatTargetG = fatTargetG; }
    public BigDecimal getFiberTargetG() { return fiberTargetG; }
    public void setFiberTargetG(BigDecimal fiberTargetG) { this.fiberTargetG = fiberTargetG; }
    public Integer getWaterTargetMl() { return waterTargetMl; }
    public void setWaterTargetMl(Integer waterTargetMl) { this.waterTargetMl = waterTargetMl; }
    public String getReferenceSystem() { return referenceSystem; }
    public void setReferenceSystem(String referenceSystem) { this.referenceSystem = referenceSystem; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public void setOnboardingCompleted(boolean onboardingCompleted) { this.onboardingCompleted = onboardingCompleted; }
    public Instant getUpdatedAt() { return updatedAt; }
}
