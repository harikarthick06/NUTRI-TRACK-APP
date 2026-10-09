package com.nutritrack.meal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedMealRepository extends JpaRepository<SavedMeal, Long> {
    List<SavedMeal> findByUserIdOrderByNameAsc(Long userId);

    Optional<SavedMeal> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}
