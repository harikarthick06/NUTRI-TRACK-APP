package com.nutritrack.diary;

import com.nutritrack.diary.DiaryDtos.CopyDayRequest;
import com.nutritrack.diary.DiaryDtos.CopyMealRequest;
import com.nutritrack.diary.DiaryDtos.CopyResult;
import com.nutritrack.diary.DiaryDtos.DayDto;
import com.nutritrack.diary.DiaryDtos.DayStatusRequest;
import com.nutritrack.diary.DiaryDtos.EntryDto;
import com.nutritrack.diary.DiaryDtos.EntryRequest;
import com.nutritrack.diary.DiaryDtos.EntryUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diary")
@Tag(name = "Food diary")
public class DiaryController {
    private final DiaryService diaryService;

    public DiaryController(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    @GetMapping
    @Operation(summary = "All entries for a day, grouped by meal, with meal and day macro totals")
    public DayDto day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return diaryService.day(date);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Log a food. Use weightState=COOKED_WEIGHT_OF_RAW_FOOD to enter a cooked weight for a raw food")
    public EntryDto add(@Valid @RequestBody EntryRequest request) {
        return diaryService.add(request);
    }

    @PutMapping("/entries/{id}")
    @Operation(summary = "Change quantity, unit, meal, date or notes of an entry")
    public EntryDto update(@PathVariable long id, @Valid @RequestBody EntryUpdateRequest request) {
        return diaryService.update(id, request);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        diaryService.delete(id);
    }

    @PostMapping("/copy-day")
    @Operation(summary = "Copy a day's entries (optionally only some meals) to another day")
    public CopyResult copyDay(@Valid @RequestBody CopyDayRequest request) {
        return diaryService.copyDay(request);
    }

    @PostMapping("/copy-meal")
    @Operation(summary = "Copy one meal to another day and/or meal")
    public CopyResult copyMeal(@Valid @RequestBody CopyMealRequest request) {
        return diaryService.copyMeal(request);
    }

    @PutMapping("/days/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark a day's log as complete or incomplete")
    public void setDayStatus(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @Valid @RequestBody DayStatusRequest request) {
        diaryService.setDayStatus(date, request.completed(), request.note());
    }
}
