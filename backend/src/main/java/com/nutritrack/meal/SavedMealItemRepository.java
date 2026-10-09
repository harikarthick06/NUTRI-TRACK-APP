package com.nutritrack.meal;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedMealItemRepository extends JpaRepository<SavedMealItem, Long> {
    List<SavedMealItem> findBySavedMealIdOrderBySortOrderAscIdAsc(Long savedMealId);

    List<SavedMealItem> findBySavedMealIdIn(Collection<Long> ids);

    @Modifying
    @Query("delete from SavedMealItem i where i.savedMealId = :id")
    void deleteBySavedMealId(@Param("id") Long id);
}
