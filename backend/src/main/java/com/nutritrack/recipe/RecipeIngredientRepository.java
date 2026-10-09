package com.nutritrack.recipe;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {
    List<RecipeIngredient> findByRecipeIdOrderBySortOrderAscIdAsc(Long recipeId);

    @Modifying
    @Query("delete from RecipeIngredient i where i.recipeId = :recipeId")
    void deleteByRecipeId(@Param("recipeId") Long recipeId);
}
