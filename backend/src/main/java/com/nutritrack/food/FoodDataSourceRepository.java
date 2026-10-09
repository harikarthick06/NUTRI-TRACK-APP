package com.nutritrack.food;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodDataSourceRepository extends JpaRepository<FoodDataSource, Integer> {
    Optional<FoodDataSource> findByCode(String code);
}
