package com.nutritrack.meal;

import com.nutritrack.common.ApiException;
import com.nutritrack.common.Enums.MealType;
import com.nutritrack.common.Enums.WeightState;
import com.nutritrack.diary.DiaryEntry;
import com.nutritrack.diary.DiaryEntryRepository;
import com.nutritrack.diary.DiaryService;
import com.nutritrack.food.Food;
import com.nutritrack.food.FoodPortionRepository;
import com.nutritrack.food.FoodRepository;
import com.nutritrack.food.FoodService;
import com.nutritrack.nutrition.QuantityResolver;
import com.nutritrack.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedMealService {
    private final SavedMealRepository meals;
    private final SavedMealItemRepository items;
    private final FoodService foodService;
    private final FoodRepository foods;
    private final FoodPortionRepository portions;
    private final QuantityResolver quantityResolver;
    private final DiaryEntryRepository diaryEntries;
    private final DiaryService diaryService;
    private final NamedParameterJdbcTemplate jdbc;

    public SavedMealService(SavedMealRepository meals, SavedMealItemRepository items, FoodService foodService,
                            FoodRepository foods, FoodPortionRepository portions, QuantityResolver quantityResolver,
                            DiaryEntryRepository diaryEntries, DiaryService diaryService, NamedParameterJdbcTemplate jdbc) {
        this.meals = meals;
        this.items = items;
        this.foodService = foodService;
        this.foods = foods;
        this.portions = portions;
        this.quantityResolver = quantityResolver;
        this.diaryEntries = diaryEntries;
        this.diaryService = diaryService;
        this.jdbc = jdbc;
    }

    public record ItemInput(@NotNull Long foodId,
                            @NotNull @DecimalMin(value = "0.001") @DecimalMax("100000") BigDecimal quantity,
                            @NotBlank @Size(max = 16) String unit, Long portionId, WeightState weightState) {
    }

    public record SavedMealRequest(@NotBlank @Size(max = 150) String name, MealType mealType,
                                   @Size(max = 500) String notes,
                                   @NotEmpty @Size(max = 50) List<@Valid ItemInput> items) {
    }

    public record FromDiaryRequest(@NotBlank @Size(max = 150) String name, @NotNull LocalDate date,
                                   @NotNull MealType mealType) {
    }

    public record LogRequest(@NotNull LocalDate date, @NotNull MealType mealType) {
    }

    public record ItemDto(long id, long foodId, String foodName, BigDecimal quantity, String unit, Long portionId,
                          WeightState weightState, BigDecimal basisAmount, BigDecimal energyKcal) {
    }

    public record SavedMealDto(long id, String name, MealType mealType, String notes, List<ItemDto> items,
                               BigDecimal energyKcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG) {
    }

    @Transactional(readOnly = true)
    public List<SavedMealDto> list() {
        long uid = CurrentUser.id();
        return meals.findByUserIdOrderByNameAsc(uid).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SavedMealDto get(long id) {
        return toDto(owned(id, CurrentUser.id()));
    }

    @Transactional
    public SavedMealDto create(SavedMealRequest req) {
        long uid = CurrentUser.id();
        if (meals.existsByUserIdAndNameIgnoreCase(uid, req.name().trim())) {
            throw ApiException.conflict("NAME_TAKEN", "You already have a saved meal with this name");
        }
        SavedMeal m = new SavedMeal();
        m.setUserId(uid);
        m.setName(req.name().trim());
        m.setMealType(req.mealType());
        m.setNotes(req.notes());
        meals.saveAndFlush(m);
        saveItems(m, req.items(), uid);
        return toDto(m);
    }

    @Transactional
    public SavedMealDto update(long id, SavedMealRequest req) {
        long uid = CurrentUser.id();
        SavedMeal m = owned(id, uid);
        if (!m.getName().equalsIgnoreCase(req.name().trim()) && meals.existsByUserIdAndNameIgnoreCase(uid, req.name().trim())) {
            throw ApiException.conflict("NAME_TAKEN", "You already have a saved meal with this name");
        }
        m.setName(req.name().trim());
        m.setMealType(req.mealType());
        m.setNotes(req.notes());
        items.deleteBySavedMealId(m.getId());
        items.flush();
        saveItems(m, req.items(), uid);
        return toDto(m);
    }

    /** Saves what was logged in one meal of one day as a reusable meal. */
    @Transactional
    public SavedMealDto createFromDiary(FromDiaryRequest req) {
        long uid = CurrentUser.id();
        List<DiaryEntry> entries = diaryEntries.findByUserIdAndEntryDateAndMealTypeOrderByLoggedAtAscIdAsc(uid,
                req.date(), req.mealType());
        if (entries.isEmpty()) {
            throw ApiException.badRequest("EMPTY_MEAL", "There is nothing logged in that meal");
        }
        List<ItemInput> inputs = entries.stream().map(e -> new ItemInput(e.getFoodId(), e.getQuantity(), e.getUnit(),
                e.getPortionId(), e.getWeightState())).toList();
        return create(new SavedMealRequest(req.name(), req.mealType(), null, inputs));
    }

    @Transactional
    public void delete(long id) {
        SavedMeal m = owned(id, CurrentUser.id());
        meals.delete(m);
    }

    @Transactional
    public int log(long id, LogRequest req) {
        long uid = CurrentUser.id();
        SavedMeal m = owned(id, uid);
        List<SavedMealItem> list = items.findBySavedMealIdOrderBySortOrderAscIdAsc(m.getId());
        for (SavedMealItem i : list) {
            Food f = foodService.visibleFood(i.getFoodId(), uid);
            if (!f.isActive()) {
                throw ApiException.badRequest("FOOD_ARCHIVED", "'" + f.getName() + "' in this meal has been archived");
            }
            diaryService.addResolved(uid, i.getFoodId(), req.date(), req.mealType(), i.getQuantity(), i.getUnit(),
                    i.getPortionId(), i.getWeightState(), i.getBasisAmount());
        }
        return list.size();
    }

    private void saveItems(SavedMeal m, List<ItemInput> inputs, long uid) {
        List<SavedMealItem> rows = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            ItemInput in = inputs.get(i);
            Food f = foodService.visibleFood(in.foodId(), uid);
            BigDecimal basis = quantityResolver.resolve(f, portions.findByFoodIdOrderByIdAsc(f.getId()), in.quantity(),
                    in.unit(), in.portionId(), in.weightState()).basisAmount();
            rows.add(new SavedMealItem(m.getId(), f.getId(), in.quantity(), in.unit().trim().toUpperCase(),
                    in.portionId(), in.weightState() == null ? WeightState.AS_DEFINED : in.weightState(), basis, i));
        }
        items.saveAll(rows);
    }

    private SavedMeal owned(long id, long uid) {
        return meals.findByIdAndUserId(id, uid).orElseThrow(() -> ApiException.notFound("Saved meal"));
    }

    private SavedMealDto toDto(SavedMeal m) {
        List<SavedMealItem> list = items.findBySavedMealIdOrderBySortOrderAscIdAsc(m.getId());
        Map<Long, Food> foodById = list.isEmpty() ? Map.of() : foods.findAllByIdIn(list.stream()
                .map(SavedMealItem::getFoodId).distinct().toList()).stream()
                .collect(Collectors.toMap(Food::getId, Function.identity()));
        Map<Long, Map<String, BigDecimal>> perItem = new java.util.HashMap<>();
        if (!list.isEmpty()) {
            jdbc.query("""
                    SELECT i.id, n.code, fn.amount * i.basis_amount / f.basis_quantity AS amount
                    FROM saved_meal_items i JOIN foods f ON f.id = i.food_id
                    JOIN food_nutrients fn ON fn.food_id = i.food_id
                    JOIN nutrient_definitions n ON n.id = fn.nutrient_id
                    WHERE i.saved_meal_id = :mid AND n.code IN ('ENERGY_KCAL','PROTEIN','CARBOHYDRATE','FAT_TOTAL')
                    """, new MapSqlParameterSource("mid", m.getId()), rs -> {
                perItem.computeIfAbsent(rs.getLong(1), k -> new java.util.HashMap<>()).put(rs.getString(2),
                        rs.getBigDecimal(3));
            });
        }
        List<ItemDto> dtos = list.stream().map(i -> new ItemDto(i.getId(), i.getFoodId(),
                foodById.containsKey(i.getFoodId()) ? foodById.get(i.getFoodId()).getName() : "Unknown food",
                i.getQuantity().stripTrailingZeros(), i.getUnit(), i.getPortionId(), i.getWeightState(),
                i.getBasisAmount().stripTrailingZeros(), round(perItem.getOrDefault(i.getId(), Map.of()).get("ENERGY_KCAL"), 0)))
                .toList();
        return new SavedMealDto(m.getId(), m.getName(), m.getMealType(), m.getNotes(), dtos,
                sum(perItem, "ENERGY_KCAL", 0), sum(perItem, "PROTEIN", 1), sum(perItem, "CARBOHYDRATE", 1),
                sum(perItem, "FAT_TOTAL", 1));
    }

    private static BigDecimal sum(Map<Long, Map<String, BigDecimal>> perItem, String code, int scale) {
        return perItem.values().stream().map(m -> m.get(code)).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MathContext.DECIMAL64)).setScale(scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal round(BigDecimal v, int scale) {
        return v == null ? null : v.setScale(scale, RoundingMode.HALF_UP);
    }
}
