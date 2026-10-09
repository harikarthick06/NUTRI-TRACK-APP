package com.nutritrack.meal;

import com.nutritrack.meal.SavedMealService.FromDiaryRequest;
import com.nutritrack.meal.SavedMealService.LogRequest;
import com.nutritrack.meal.SavedMealService.SavedMealDto;
import com.nutritrack.meal.SavedMealService.SavedMealRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/saved-meals")
@Tag(name = "Saved meals")
public class SavedMealController {
    private final SavedMealService service;

    public SavedMealController(SavedMealService service) {
        this.service = service;
    }

    @GetMapping
    public List<SavedMealDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public SavedMealDto get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedMealDto create(@Valid @RequestBody SavedMealRequest request) {
        return service.create(request);
    }

    @PostMapping("/from-diary")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a logged meal (date + meal) as a reusable meal")
    public SavedMealDto fromDiary(@Valid @RequestBody FromDiaryRequest request) {
        return service.createFromDiary(request);
    }

    @PutMapping("/{id}")
    public SavedMealDto update(@PathVariable long id, @Valid @RequestBody SavedMealRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/log")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add every item of the saved meal to the diary")
    public Map<String, Integer> log(@PathVariable long id, @Valid @RequestBody LogRequest request) {
        return Map.of("loggedEntries", service.log(id, request));
    }
}
