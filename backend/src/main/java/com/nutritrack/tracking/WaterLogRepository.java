package com.nutritrack.tracking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WaterLogRepository extends JpaRepository<WaterLog, Long> {
    List<WaterLog> findByUserIdAndLogDateOrderByLoggedAtAsc(Long userId, LocalDate logDate);

    Optional<WaterLog> findByIdAndUserId(Long id, Long userId);

    interface DailyTotal {
        LocalDate getLogDate();

        Long getTotalMl();
    }

    @Query("select w.logDate as logDate, sum(w.amountMl) as totalMl from WaterLog w where w.userId = :userId"
            + " and w.logDate between :from and :to group by w.logDate order by w.logDate")
    List<DailyTotal> dailyTotals(Long userId, LocalDate from, LocalDate to);
}
