package com.nutritrack.nutrient;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutrientDefinitionRepository extends JpaRepository<NutrientDefinition, Integer> {
    List<NutrientDefinition> findAllByOrderByDisplayOrderAsc();
}
