package com.nutritrack.diary;

import com.nutritrack.common.Enums.MealType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, Long> {
    List<DiaryEntry> findByUserIdAndEntryDateOrderByLoggedAtAscIdAsc(Long userId, LocalDate date);

    List<DiaryEntry> findByUserIdAndEntryDateAndMealTypeOrderByLoggedAtAscIdAsc(Long userId, LocalDate date,
                                                                               MealType mealType);

    List<DiaryEntry> findByUserIdAndEntryDateBetweenOrderByEntryDateAscLoggedAtAscIdAsc(Long userId, LocalDate from,
                                                                                      LocalDate to);

    Optional<DiaryEntry> findByIdAndUserId(Long id, Long userId);

    boolean existsByFoodId(Long foodId);
}
