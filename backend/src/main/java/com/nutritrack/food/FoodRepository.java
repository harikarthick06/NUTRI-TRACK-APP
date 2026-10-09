package com.nutritrack.food;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodRepository extends JpaRepository<Food, Long> {
    Optional<Food> findBySourceIdAndSourceFoodRef(Integer sourceId, String sourceFoodRef);

    Optional<Food> findByRecipeId(Long recipeId);

    @Query("select f from Food f where f.id in :ids")
    List<Food> findAllByIdIn(@Param("ids") List<Long> ids);
}
