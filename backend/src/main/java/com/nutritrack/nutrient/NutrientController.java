package com.nutritrack.nutrient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nutrients")
@Tag(name = "Nutrients")
public class NutrientController {
    private final NutrientCatalog catalog;

    public NutrientController(NutrientCatalog catalog) {
        this.catalog = catalog;
    }

    public record NutrientDto(String code, String name, String shortName, String unit, String category,
                              String formNote, int displayOrder) {
    }

    @GetMapping
    @Operation(summary = "All tracked nutrients with their canonical units")
    public List<NutrientDto> all() {
        return catalog.all().stream().map(n -> new NutrientDto(n.getCode(), n.getName(), n.getShortName(), n.getUnit(),
                n.getCategory(), n.getFormNote(), n.getDisplayOrder())).toList();
    }
}
