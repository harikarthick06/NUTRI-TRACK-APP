package com.nutritrack.food;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodNutrientRepository extends JpaRepository<FoodNutrient, FoodNutrient.Key> {
    List<FoodNutrient> findByFoodId(Long foodId);

    List<FoodNutrient> findByFoodIdIn(Collection<Long> foodIds);

    @Modifying
    @Query("delete from FoodNutrient fn where fn.foodId = :foodId")
    void deleteByFoodId(@Param("foodId") Long foodId);
}
