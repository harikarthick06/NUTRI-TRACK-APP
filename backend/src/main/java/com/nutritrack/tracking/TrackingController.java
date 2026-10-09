package com.nutritrack.tracking;

import com.nutritrack.tracking.WaterService.WaterDay;
import com.nutritrack.tracking.WaterService.WaterHistory;
import com.nutritrack.tracking.WaterService.WaterRequest;
import com.nutritrack.tracking.WeightService.WeightDto;
import com.nutritrack.tracking.WeightService.WeightHistory;
import com.nutritrack.tracking.WeightService.WeightRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Weight and water")
public class TrackingController {
    private final WeightService weight;
    private final WaterService water;

    public TrackingController(WeightService weight, WaterService water) {
        this.weight = weight;
        this.water = water;
    }

    @GetMapping({"/api/weight-logs", "/api/analytics/weight"})
    @Operation(summary = "Weight entries in a date range (default: last 90 days) with summary statistics")
    public WeightHistory weightHistory(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return weight.history(from, to);
    }

    @PostMapping("/api/weight-logs")
    @ResponseStatus(HttpStatus.CREATED)
    public WeightDto addWeight(@Valid @RequestBody WeightRequest request) {
        return weight.create(request);
    }

    @PutMapping("/api/weight-logs/{id}")
    public WeightDto updateWeight(@PathVariable long id, @Valid @RequestBody WeightRequest request) {
        return weight.update(id, request);
    }

    @DeleteMapping("/api/weight-logs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWeight(@PathVariable long id) {
        weight.delete(id);
    }

    @GetMapping("/api/water-logs")
    @Operation(summary = "Water entries and total for one day (default: today)")
    public WaterDay waterDay(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return water.day(date);
    }

    @GetMapping("/api/water-logs/history")
    @Operation(summary = "Daily water totals in a date range (default: last 30 days)")
    public WaterHistory waterHistory(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return water.history(from, to);
    }

    @PostMapping("/api/water-logs")
    @ResponseStatus(HttpStatus.CREATED)
    public WaterDay addWater(@Valid @RequestBody WaterRequest request) {
        return water.add(request);
    }

    @PutMapping("/api/water-logs/{id}")
    public WaterDay updateWater(@PathVariable long id, @Valid @RequestBody WaterRequest request) {
        return water.update(id, request);
    }

    @DeleteMapping("/api/water-logs/{id}")
    public WaterDay deleteWater(@PathVariable long id) {
        return water.delete(id);
    }
}
