package com.nutritrack.recipe;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    @Query("select r from Recipe r where r.ownerUserId = :uid or r.system = true order by r.system asc, r.name asc")
    List<Recipe> findVisible(@Param("uid") Long uid);

    List<Recipe> findBySystemTrueAndFoodIdIsNull();

    Optional<Recipe> findByFoodId(Long foodId);
}
