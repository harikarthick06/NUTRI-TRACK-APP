package com.nutritrack.nutrition;

import com.nutritrack.nutrition.NutritionSummaryService.DailyNutrition;
import com.nutritrack.nutrition.NutritionSummaryService.MicronutrientView;
import com.nutritrack.nutrition.NutritionSummaryService.TargetStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nutrition")
@Tag(name = "Nutrition summaries")
public class NutritionController {
    private final NutritionSummaryService service;

    public NutritionController(NutritionSummaryService service) {
        this.service = service;
    }

    @GetMapping("/daily")
    @Operation(summary = "Every nutrient for one day, with data coverage and comparison with targets")
    public DailyNutrition daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.daily(date);
    }

    @GetMapping("/micronutrients")
    @Operation(summary = "Vitamins, minerals, fatty acids and other nutrients for one day, filterable")
    public MicronutrientView micronutrients(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String category, @RequestParam(required = false) String q,
            @RequestParam(required = false) TargetStatus status) {
        return service.micronutrients(date, category, q, status);
    }
}
