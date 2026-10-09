package com.nutritrack.target;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserNutrientTargetRepository extends JpaRepository<UserNutrientTarget, Long> {
    List<UserNutrientTarget> findByUserId(Long userId);

    Optional<UserNutrientTarget> findByUserIdAndNutrientId(Long userId, Integer nutrientId);

    @Modifying
    @Query("delete from UserNutrientTarget t where t.userId = :userId")
    int deleteAllForUser(Long userId);
}
