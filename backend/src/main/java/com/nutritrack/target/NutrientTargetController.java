package com.nutritrack.target;

import com.nutritrack.target.NutrientTargetService.TargetUpdateRequest;
import com.nutritrack.target.NutrientTargetService.TargetsResponse;
import com.nutritrack.target.ReferenceValueRepository.ReferenceRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Nutrient targets")
public class NutrientTargetController {
    private final NutrientTargetService service;
    private final ReferenceValueRepository references;

    public NutrientTargetController(NutrientTargetService service, ReferenceValueRepository references) {
        this.service = service;
        this.references = references;
    }

    @GetMapping("/api/nutrient-targets")
    @Operation(summary = "Daily targets and upper limits for every nutrient, with where each value came from")
    public TargetsResponse list() {
        return service.list();
    }

    @PutMapping("/api/nutrient-targets")
    @Operation(summary = "Set your own targets or upper limits; send both as null to go back to the reference value")
    public TargetsResponse update(@Valid @RequestBody TargetUpdateRequest request) {
        return service.update(request);
    }

    @DeleteMapping("/api/nutrient-targets")
    @Operation(summary = "Remove all custom targets")
    public TargetsResponse resetAll() {
        return service.reset(null);
    }

    @DeleteMapping("/api/nutrient-targets/{code}")
    @Operation(summary = "Remove the custom target for one nutrient")
    public TargetsResponse reset(@PathVariable String code) {
        return service.reset(code);
    }

    public record ReferenceValuesResponse(List<String> systems, String disclaimer, List<ReferenceRow> values) {
    }

    @GetMapping("/api/reference-values")
    @Operation(summary = "The reference value table, with sources")
    public ReferenceValuesResponse referenceValues(@RequestParam(required = false) String system,
                                                   @RequestParam(required = false) String code) {
        return new ReferenceValuesResponse(references.systems(), NutrientTargetService.DISCLAIMER,
                references.search(system, code));
    }
}
