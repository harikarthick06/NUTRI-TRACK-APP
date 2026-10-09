package com.nutritrack.food;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodPortionRepository extends JpaRepository<FoodPortion, Long> {
    List<FoodPortion> findByFoodIdOrderByIdAsc(Long foodId);

    List<FoodPortion> findByFoodIdIn(Collection<Long> foodIds);

    @Modifying
    @Query("delete from FoodPortion p where p.foodId = :foodId")
    void deleteByFoodId(@Param("foodId") Long foodId);
}
