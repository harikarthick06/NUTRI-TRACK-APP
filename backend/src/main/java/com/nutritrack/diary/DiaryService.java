package com.nutritrack.diary;

import com.nutritrack.common.ApiException;
import com.nutritrack.common.Enums.MealType;
import com.nutritrack.common.Enums.WeightState;
import com.nutritrack.diary.DiaryDtos.CopyDayRequest;
import com.nutritrack.diary.DiaryDtos.CopyMealRequest;
import com.nutritrack.diary.DiaryDtos.CopyResult;
import com.nutritrack.diary.DiaryDtos.DayDto;
import com.nutritrack.diary.DiaryDtos.EntryDto;
import com.nutritrack.diary.DiaryDtos.EntryRequest;
import com.nutritrack.diary.DiaryDtos.EntryUpdateRequest;
import com.nutritrack.diary.DiaryDtos.MacroTotal;
import com.nutritrack.diary.DiaryDtos.Macros;
import com.nutritrack.diary.DiaryDtos.MealDto;
import com.nutritrack.food.Food;
import com.nutritrack.food.FoodPortion;
import com.nutritrack.food.FoodPortionRepository;
import com.nutritrack.food.FoodRepository;
import com.nutritrack.food.FoodService;
import com.nutritrack.nutrition.NutritionAggregationRepository;
import com.nutritrack.nutrition.NutritionAggregationRepository.Total;
import com.nutritrack.nutrition.QuantityResolver;
import com.nutritrack.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryService {
    private static final List<String> MACROS = NutritionAggregationRepository.macroCodes();

    private final DiaryEntryRepository entries;
    private final FoodService foodService;
    private final FoodRepository foods;
    private final FoodPortionRepository portions;
    private final QuantityResolver quantityResolver;
    private final NutritionAggregationRepository aggregation;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public DiaryService(DiaryEntryRepository entries, FoodService foodService, FoodRepository foods,
                        FoodPortionRepository portions, QuantityResolver quantityResolver,
                        NutritionAggregationRepository aggregation, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.entries = entries;
        this.foodService = foodService;
        this.foods = foods;
        this.portions = portions;
        this.quantityResolver = quantityResolver;
        this.aggregation = aggregation;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DayDto day(LocalDate date) {
        long uid = CurrentUser.id();
        checkDate(date);
        List<DiaryEntry> list = entries.findByUserIdAndEntryDateOrderByLoggedAtAscIdAsc(uid, date);
        Map<Long, Map<String, BigDecimal>> perEntry = aggregation.entryNutrients(uid,
                list.stream().map(DiaryEntry::getId).toList(), MACROS);
        Map<Long, Food> foodById = foods.findAllByIdIn(list.stream().map(DiaryEntry::getFoodId).distinct().toList())
                .stream().collect(Collectors.toMap(Food::getId, Function.identity()));
        Map<Long, FoodPortion> portionById = list.stream().map(DiaryEntry::getPortionId).filter(java.util.Objects::nonNull)
                .distinct().map(portions::findById).flatMap(java.util.Optional::stream)
                .collect(Collectors.toMap(FoodPortion::getId, Function.identity()));

        Map<String, Map<String, Total>> mealTotals = aggregation.totalsByMeal(uid, date);
        Map<String, Integer> mealCounts = aggregation.entryCountsByMeal(uid, date);
        List<MealDto> meals = new ArrayList<>();
        Map<String, Total> dayTotals = new HashMap<>();
        for (MealType meal : MealType.values()) {
            List<EntryDto> mealEntries = list.stream().filter(e -> e.getMealType() == meal)
                    .map(e -> toDto(e, foodById.get(e.getFoodId()), portionById.get(e.getPortionId()),
                            perEntry.getOrDefault(e.getId(), Map.of())))
                    .toList();
            Map<String, Total> totals = mealTotals.getOrDefault(meal.name(), Map.of());
            meals.add(new MealDto(meal, mealEntries, macros(totals, mealCounts.getOrDefault(meal.name(), 0))));
            totals.forEach((code, t) -> dayTotals.merge(code, t, (a, b) -> new Total(code, a.amount().add(b.amount()),
                    a.entriesWithData() + b.entriesWithData(), a.entriesPartial() + b.entriesPartial())));
        }
        var status = jdbc.query("SELECT completed, note FROM diary_days WHERE user_id = :uid AND day_date = :d",
                new MapSqlParameterSource("uid", uid).addValue("d", date),
                (rs, i) -> new Object[] {rs.getBoolean(1), rs.getString(2)});
        boolean complete = !status.isEmpty() && (Boolean) status.get(0)[0];
        String note = status.isEmpty() ? null : (String) status.get(0)[1];
        return new DayDto(date, meals, macros(dayTotals, list.size()), list.size(), complete, note);
    }

    @Transactional
    public EntryDto add(EntryRequest r) {
        long uid = CurrentUser.id();
        checkDate(r.date());
        Food food = foodService.visibleFood(r.foodId(), uid);
        if (!food.isActive()) {
            throw ApiException.badRequest("FOOD_ARCHIVED", "This food has been archived");
        }
        DiaryEntry e = new DiaryEntry();
        e.setUserId(uid);
        e.setFoodId(food.getId());
        e.setEntryDate(r.date());
        e.setMealType(r.mealType());
        e.setLoggedAt(r.loggedAt() == null ? clock.instant() : r.loggedAt());
        e.setNotes(blankToNull(r.notes()));
        applyQuantity(e, food, r.quantity(), r.unit(), r.portionId(), r.weightState());
        entries.saveAndFlush(e);
        return single(e, food, uid);
    }

    @Transactional
    public EntryDto update(long id, EntryUpdateRequest r) {
        long uid = CurrentUser.id();
        DiaryEntry e = owned(id, uid);
        Food food = foods.findById(e.getFoodId()).orElseThrow(() -> ApiException.notFound("Food"));
        applyQuantity(e, food, r.quantity(), r.unit(), r.portionId(), r.weightState());
        if (r.mealType() != null) {
            e.setMealType(r.mealType());
        }
        if (r.date() != null) {
            checkDate(r.date());
            e.setEntryDate(r.date());
        }
        if (r.loggedAt() != null) {
            e.setLoggedAt(r.loggedAt());
        }
        e.setNotes(blankToNull(r.notes()));
        entries.saveAndFlush(e);
        return single(e, food, uid);
    }

    @Transactional
    public void delete(long id) {
        long uid = CurrentUser.id();
        entries.delete(owned(id, uid));
    }

    /** Copies entries from one day to another; existing entries on the target day are kept. */
    @Transactional
    public CopyResult copyDay(CopyDayRequest r) {
        long uid = CurrentUser.id();
        checkDate(r.fromDate());
        checkDate(r.toDate());
        if (r.fromDate().equals(r.toDate())) {
            throw ApiException.badRequest("SAME_DAY", "Choose a different day to copy to");
        }
        List<DiaryEntry> source = entries.findByUserIdAndEntryDateOrderByLoggedAtAscIdAsc(uid, r.fromDate()).stream()
                .filter(e -> r.mealTypes() == null || r.mealTypes().isEmpty() || r.mealTypes().contains(e.getMealType()))
                .toList();
        return new CopyResult(copy(source, uid, r.toDate(), null), r.toDate());
    }

    /** Copies one meal (e.g. yesterday's breakfast) into a meal on another or the same day. */
    @Transactional
    public CopyResult copyMeal(CopyMealRequest r) {
        long uid = CurrentUser.id();
        checkDate(r.fromDate());
        checkDate(r.toDate());
        List<DiaryEntry> source = entries.findByUserIdAndEntryDateAndMealTypeOrderByLoggedAtAscIdAsc(uid, r.fromDate(),
                r.fromMeal());
        if (source.isEmpty()) {
            throw ApiException.badRequest("EMPTY_MEAL", "There is nothing logged in that meal");
        }
        return new CopyResult(copy(source, uid, r.toDate(), r.toMeal()), r.toDate());
    }

    private int copy(List<DiaryEntry> source, long uid, LocalDate toDate, MealType toMeal) {
        Instant now = clock.instant();
        List<DiaryEntry> copies = new ArrayList<>();
        for (DiaryEntry s : source) {
            DiaryEntry c = new DiaryEntry();
            c.setUserId(uid);
            c.setFoodId(s.getFoodId());
            c.setEntryDate(toDate);
            c.setMealType(toMeal == null ? s.getMealType() : toMeal);
            c.setLoggedAt(now);
            c.setQuantity(s.getQuantity());
            c.setUnit(s.getUnit());
            c.setPortionId(s.getPortionId());
            c.setWeightState(s.getWeightState());
            c.setBasisAmount(s.getBasisAmount());
            c.setNotes(s.getNotes());
            copies.add(c);
        }
        entries.saveAll(copies);
        return copies.size();
    }

    @Transactional
    public void setDayStatus(LocalDate date, boolean completed, String note) {
        long uid = CurrentUser.id();
        checkDate(date);
        jdbc.update("INSERT INTO diary_days (user_id, day_date, completed, note) VALUES (:uid, :d, :c, :n)"
                        + " ON DUPLICATE KEY UPDATE completed = VALUES(completed), note = VALUES(note)",
                new MapSqlParameterSource("uid", uid).addValue("d", date).addValue("c", completed)
                        .addValue("n", blankToNull(note)));
    }

    /** Adds an entry for an already resolved amount (used when logging saved meals). */
    @Transactional
    public void addResolved(long uid, long foodId, LocalDate date, MealType meal, BigDecimal quantity, String unit,
                            Long portionId, WeightState state, BigDecimal basisAmount) {
        DiaryEntry e = new DiaryEntry();
        e.setUserId(uid);
        e.setFoodId(foodId);
        e.setEntryDate(date);
        e.setMealType(meal);
        e.setLoggedAt(clock.instant());
        e.setQuantity(quantity);
        e.setUnit(unit);
        e.setPortionId(portionId);
        e.setWeightState(state);
        e.setBasisAmount(basisAmount);
        entries.save(e);
    }

    private void applyQuantity(DiaryEntry e, Food food, BigDecimal quantity, String unit, Long portionId,
                               WeightState state) {
        List<FoodPortion> foodPortions = portions.findByFoodIdOrderByIdAsc(food.getId());
        var resolved = quantityResolver.resolve(food, foodPortions, quantity, unit, portionId, state);
        e.setQuantity(quantity);
        e.setUnit(unit.trim().toUpperCase());
        e.setPortionId(portionId);
        e.setWeightState(state == null ? WeightState.AS_DEFINED : state);
        e.setBasisAmount(resolved.basisAmount());
    }

    private EntryDto single(DiaryEntry e, Food food, long uid) {
        Map<String, BigDecimal> values = aggregation.entryNutrients(uid, List.of(e.getId()), MACROS)
                .getOrDefault(e.getId(), Map.of());
        FoodPortion portion = e.getPortionId() == null ? null : portions.findById(e.getPortionId()).orElse(null);
        return toDto(e, food, portion, values);
    }

    private DiaryEntry owned(long id, long uid) {
        return entries.findByIdAndUserId(id, uid).orElseThrow(() -> ApiException.notFound("Diary entry"));
    }

    private void checkDate(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today.minusYears(10)) || date.isAfter(today.plusYears(1))) {
            throw ApiException.badRequest("DATE_OUT_OF_RANGE", "Date must be within the last 10 years and the next year");
        }
    }

    static Macros macros(Map<String, Total> totals, int entries) {
        return new Macros(macro(totals.get("ENERGY_KCAL"), entries, 0), macro(totals.get("PROTEIN"), entries, 1),
                macro(totals.get("CARBOHYDRATE"), entries, 1), macro(totals.get("FAT_TOTAL"), entries, 1),
                macro(totals.get("FIBER"), entries, 1));
    }

    private static MacroTotal macro(Total t, int entries, int scale) {
        if (t == null) {
            return new MacroTotal(entries == 0 ? BigDecimal.ZERO : null, 0, entries);
        }
        return new MacroTotal(t.amount().setScale(scale, RoundingMode.HALF_UP), t.entriesWithData(), entries);
    }

    private static EntryDto toDto(DiaryEntry e, Food f, FoodPortion portion, Map<String, BigDecimal> v) {
        List<String> unknown = MACROS.stream().filter(c -> !v.containsKey(c)).toList();
        return new EntryDto(e.getId(), e.getFoodId(), f == null ? "Unknown food" : f.getName(),
                f == null ? null : f.getBrand(), f == null ? null : f.getDataQuality(),
                f == null ? null : f.getCompletenessPct(), f == null ? null : f.getPreparationState(), e.getMealType(),
                e.getEntryDate(), e.getLoggedAt(), e.getQuantity().stripTrailingZeros(), e.getUnit(), e.getPortionId(),
                portion == null ? null : portion.getLabel(), e.getWeightState(), e.getBasisAmount().stripTrailingZeros(),
                f == null ? null : f.getBasisUnit(), e.getNotes(), r(v.get("ENERGY_KCAL"), 0), r(v.get("PROTEIN"), 1),
                r(v.get("CARBOHYDRATE"), 1), r(v.get("FAT_TOTAL"), 1), r(v.get("FIBER"), 1), unknown);
    }

    private static BigDecimal r(BigDecimal v, int scale) {
        return v == null ? null : v.setScale(scale, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
