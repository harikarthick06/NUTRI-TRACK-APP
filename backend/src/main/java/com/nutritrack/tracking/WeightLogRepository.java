package com.nutritrack.tracking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightLogRepository extends JpaRepository<WeightLog, Long> {
    List<WeightLog> findByUserIdAndLogDateBetweenOrderByLogDateAsc(Long userId, LocalDate from, LocalDate to);

    Optional<WeightLog> findByIdAndUserId(Long id, Long userId);

    Optional<WeightLog> findByUserIdAndLogDate(Long userId, LocalDate logDate);

    Optional<WeightLog> findFirstByUserIdOrderByLogDateDesc(Long userId);

    Optional<WeightLog> findFirstByUserIdOrderByLogDateAsc(Long userId);
}
