package com.nutritrack.recipe;

import com.nutritrack.common.ApiException;
import com.nutritrack.common.Enums.BasisUnit;
import com.nutritrack.common.Enums.DataQuality;
import com.nutritrack.common.Enums.FoodType;
import com.nutritrack.common.Enums.PreparationState;
import com.nutritrack.common.Enums.WeightState;
import com.nutritrack.diary.DiaryDtos.EntryDto;
import com.nutritrack.diary.DiaryDtos.EntryRequest;
import com.nutritrack.diary.DiaryService;
import com.nutritrack.food.Food;
import com.nutritrack.food.FoodDataSource;
import com.nutritrack.food.FoodDataSourceRepository;
import com.nutritrack.food.FoodNutrient;
import com.nutritrack.food.FoodNutrientRepository;
import com.nutritrack.food.FoodPortion;
import com.nutritrack.food.FoodPortionRepository;
import com.nutritrack.food.FoodRepository;
import com.nutritrack.food.FoodService;
import com.nutritrack.nutrient.NutrientCatalog;
import com.nutritrack.nutrient.NutrientDefinition;
import com.nutritrack.nutrition.QuantityResolver;
import com.nutritrack.nutrition.RecipeCalculator;
import com.nutritrack.recipe.RecipeDtos.IngredientDto;
import com.nutritrack.recipe.RecipeDtos.IngredientInput;
import com.nutritrack.recipe.RecipeDtos.LogRecipeRequest;
import com.nutritrack.recipe.RecipeDtos.RecipeDetail;
import com.nutritrack.recipe.RecipeDtos.RecipeNutrientDto;
import com.nutritrack.recipe.RecipeDtos.RecipeNutrition;
import com.nutritrack.recipe.RecipeDtos.RecipeRequest;
import com.nutritrack.recipe.RecipeDtos.RecipeSummary;
import com.nutritrack.security.CurrentUser;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipeService {
    private static final MathContext MC = MathContext.DECIMAL64;
    static final String WHOLE_RECIPE_LABEL = "Whole recipe";

    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final FoodRepository foods;
    private final FoodNutrientRepository foodNutrients;
    private final FoodPortionRepository portions;
    private final FoodDataSourceRepository sources;
    private final FoodService foodService;
    private final NutrientCatalog catalog;
    private final QuantityResolver quantityResolver;
    private final DiaryService diaryService;

    public RecipeService(RecipeRepository recipes, RecipeIngredientRepository ingredients, FoodRepository foods,
                         FoodNutrientRepository foodNutrients, FoodPortionRepository portions,
                         FoodDataSourceRepository sources, FoodService foodService, NutrientCatalog catalog,
                         QuantityResolver quantityResolver, DiaryService diaryService) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.foods = foods;
        this.foodNutrients = foodNutrients;
        this.portions = portions;
        this.sources = sources;
        this.foodService = foodService;
        this.catalog = catalog;
        this.quantityResolver = quantityResolver;
        this.diaryService = diaryService;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<RecipeSummary> list() {
        long uid = CurrentUser.id();
        List<Recipe> visible = recipes.findVisible(uid);
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Map<String, BigDecimal>> perFood = new HashMap<>();
        List<Long> foodIds = visible.stream().map(Recipe::getFoodId).filter(java.util.Objects::nonNull).toList();
        Map<Integer, String> codes = catalog.all().stream().collect(Collectors.toMap(NutrientDefinition::getId,
                NutrientDefinition::getCode));
        for (FoodNutrient fn : foodIds.isEmpty() ? List.<FoodNutrient>of() : foodNutrients.findByFoodIdIn(foodIds)) {
            String code = codes.get(fn.getNutrientId());
            if ("ENERGY_KCAL".equals(code) || "PROTEIN".equals(code)) {
                perFood.computeIfAbsent(fn.getFoodId(), k -> new HashMap<>()).put(code, fn.getAmount());
            }
        }
        for (Recipe r : visible) {
            counts.put(r.getId(), ingredients.findByRecipeIdOrderBySortOrderAscIdAsc(r.getId()).size());
        }
        Map<Long, BigDecimal> servingWeights = new HashMap<>();
        for (FoodPortion p : foodIds.isEmpty() ? List.<FoodPortion>of() : portions.findByFoodIdIn(foodIds)) {
            if (p.isDefaultPortion()) {
                servingWeights.put(p.getFoodId(), p.getBasisAmount());
            }
        }
        return visible.stream().map(r -> {
            BigDecimal servingWeight = r.getFoodId() == null ? servingWeight(r) : servingWeights.get(r.getFoodId());
            Map<String, BigDecimal> m = r.getFoodId() == null ? Map.of() : perFood.getOrDefault(r.getFoodId(), Map.of());
            return new RecipeSummary(r.getId(), r.getName(), r.getDescription(), r.getServings(), servingWeight,
                    r.getServingLabel(), perServing(m.get("ENERGY_KCAL"), servingWeight, 0),
                    perServing(m.get("PROTEIN"), servingWeight, 1), r.getFoodId(), r.isSystem(),
                    r.getOwnerUserId() != null && r.getOwnerUserId() == uid, counts.getOrDefault(r.getId(), 0),
                    r.getUpdatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public RecipeDetail get(long id) {
        long uid = CurrentUser.id();
        Recipe r = visible(id, uid);
        List<RecipeIngredient> list = ingredients.findByRecipeIdOrderBySortOrderAscIdAsc(r.getId());
        Computation c = compute(list.stream().map(i -> new IngredientInput(i.getFoodId(), i.getQuantity(), i.getUnit(),
                i.getPortionId(), i.getWeightState(), i.getNote())).toList(), r.getTotalCookedWeightG(), r.getServings(),
                uid, r.getId(), list);
        return detail(r, uid, c);
    }

    /** Calculates nutrition for an unsaved recipe (live preview in the recipe builder). */
    @Transactional(readOnly = true)
    public RecipeNutrition preview(RecipeRequest req) {
        long uid = CurrentUser.id();
        return compute(req.ingredients(), req.totalCookedWeightG(), req.servings(), uid, null, null).nutrition();
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public RecipeDetail create(RecipeRequest req) {
        long uid = CurrentUser.id();
        Recipe r = new Recipe();
        r.setOwnerUserId(uid);
        apply(r, req);
        recipes.saveAndFlush(r);
        return saveIngredientsAndMaterialize(r, req, uid);
    }

    @Transactional
    public RecipeDetail update(long id, RecipeRequest req) {
        long uid = CurrentUser.id();
        Recipe r = editable(id, uid);
        apply(r, req);
        ingredients.deleteByRecipeId(r.getId());
        ingredients.flush();
        return saveIngredientsAndMaterialize(r, req, uid);
    }

    /** Copies any visible recipe (including built-in ones) into an editable recipe of your own. */
    @Transactional
    public RecipeDetail duplicate(long id) {
        long uid = CurrentUser.id();
        Recipe source = visible(id, uid);
        List<IngredientInput> inputs = ingredients.findByRecipeIdOrderBySortOrderAscIdAsc(source.getId()).stream()
                .map(i -> new IngredientInput(i.getFoodId(), i.getQuantity(), i.getUnit(), i.getPortionId(),
                        i.getWeightState(), i.getNote())).toList();
        RecipeRequest req = new RecipeRequest(source.getName() + " (my version)", source.getDescription(),
                source.getInstructions(), source.getServings(), source.getTotalCookedWeightG(), source.getServingLabel(),
                inputs);
        return create(req);
    }

    /**
     * Deletes a recipe. Its food row is archived rather than deleted so past diary entries keep their values.
     */
    @Transactional
    public void delete(long id) {
        long uid = CurrentUser.id();
        Recipe r = editable(id, uid);
        if (r.getFoodId() != null) {
            foods.findById(r.getFoodId()).ifPresent(f -> {
                f.setActive(false);
                f.setRecipeId(null);
            });
        }
        ingredients.deleteByRecipeId(r.getId());
        recipes.delete(r);
    }

    @Transactional
    public EntryDto log(long id, LogRecipeRequest req) {
        long uid = CurrentUser.id();
        Recipe r = visible(id, uid);
        if (r.getFoodId() == null) {
            throw ApiException.unprocessable("RECIPE_NOT_READY", "This recipe has no nutrition yet");
        }
        List<FoodPortion> list = portions.findByFoodIdOrderByIdAsc(r.getFoodId());
        boolean whole = Boolean.TRUE.equals(req.wholeRecipe());
        FoodPortion portion = list.stream()
                .filter(p -> whole ? p.getLabel().startsWith(WHOLE_RECIPE_LABEL) : p.isDefaultPortion())
                .findFirst().orElseThrow(() -> ApiException.unprocessable("RECIPE_NOT_READY", "Recipe portions missing"));
        BigDecimal qty = whole ? BigDecimal.ONE : req.servings();
        return diaryService.add(new EntryRequest(r.getFoodId(), req.date(), req.mealType(), qty, portion.getUnit(),
                portion.getId(), WeightState.AS_DEFINED, null, null));
    }

    /** Calculates and stores nutrients for built-in recipes that have none yet. Runs at startup. */
    @Transactional
    public int materializeSystemRecipes() {
        int n = 0;
        for (Recipe r : recipes.findBySystemTrueAndFoodIdIsNull()) {
            List<RecipeIngredient> list = ingredients.findByRecipeIdOrderBySortOrderAscIdAsc(r.getId());
            List<IngredientInput> inputs = list.stream().map(i -> new IngredientInput(i.getFoodId(), i.getQuantity(),
                    i.getUnit(), i.getPortionId(), i.getWeightState(), i.getNote())).toList();
            Computation c = compute(inputs, r.getTotalCookedWeightG(), r.getServings(), null, r.getId(), list);
            materialize(r, c);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ internals

    private RecipeDetail saveIngredientsAndMaterialize(Recipe r, RecipeRequest req, long uid) {
        Computation c = compute(req.ingredients(), r.getTotalCookedWeightG(), r.getServings(), uid, r.getId(), null);
        List<RecipeIngredient> saved = new ArrayList<>();
        for (int i = 0; i < req.ingredients().size(); i++) {
            IngredientInput in = req.ingredients().get(i);
            saved.add(new RecipeIngredient(r.getId(), in.foodId(), in.quantity(), in.unit().trim().toUpperCase(),
                    in.portionId(), in.weightState() == null ? WeightState.AS_DEFINED : in.weightState(),
                    c.resolved().get(i), i, blank(in.note())));
        }
        ingredients.saveAll(saved);
        materialize(r, c);
        return detail(r, uid, new Computation(c.nutrition(), c.result(), c.resolved(), c.grams(), c.foods(), saved));
    }

    private record Computation(RecipeNutrition nutrition, RecipeCalculator.Result result, List<BigDecimal> resolved,
                               List<BigDecimal> grams, Map<Long, Food> foods, List<RecipeIngredient> stored) {
    }

    /**
     * Resolves each ingredient's amount, checks the user may use the food, guards against a recipe containing
     * itself, and runs the calculator. {@code uid} is null for built-in recipes.
     */
    private Computation compute(List<IngredientInput> inputs, BigDecimal cookedWeight, BigDecimal servings, Long uid,
                                Long recipeId, List<RecipeIngredient> stored) {
        if (inputs.isEmpty()) {
            throw ApiException.badRequest("NO_INGREDIENTS", "Add at least one ingredient");
        }
        List<Long> foodIds = inputs.stream().map(IngredientInput::foodId).distinct().toList();
        Map<Long, Food> foodById = foods.findAllByIdIn(foodIds).stream()
                .collect(Collectors.toMap(Food::getId, Function.identity()));
        Map<Long, List<FoodPortion>> portionsByFood = portions.findByFoodIdIn(foodIds).stream()
                .collect(Collectors.groupingBy(FoodPortion::getFoodId));
        Map<Integer, NutrientDefinition> defs = catalog.all().stream()
                .collect(Collectors.toMap(NutrientDefinition::getId, Function.identity()));
        Map<Long, Map<String, BigDecimal>> values = new HashMap<>();
        Map<Long, Map<String, BigDecimal>> coverage = new HashMap<>();
        for (FoodNutrient fn : foodNutrients.findByFoodIdIn(foodIds)) {
            String code = defs.get(fn.getNutrientId()).getCode();
            values.computeIfAbsent(fn.getFoodId(), k -> new HashMap<>()).put(code, fn.getAmount());
            if (fn.getCoveragePct() != null) {
                coverage.computeIfAbsent(fn.getFoodId(), k -> new HashMap<>()).put(code, fn.getCoveragePct());
            }
        }
        List<RecipeCalculator.Ingredient> calcInputs = new ArrayList<>();
        List<BigDecimal> resolved = new ArrayList<>();
        List<BigDecimal> grams = new ArrayList<>();
        List<String> extraWarnings = new ArrayList<>();
        for (IngredientInput in : inputs) {
            Food f = foodById.get(in.foodId());
            if (f == null || (uid != null && f.getOwnerUserId() != null && !f.getOwnerUserId().equals(uid))
                    || (uid == null && f.getOwnerUserId() != null)) {
                throw ApiException.badRequest("INVALID_INGREDIENT", "Ingredient food " + in.foodId() + " not found");
            }
            if (recipeId != null && f.getRecipeId() != null && containsRecipe(f.getRecipeId(), recipeId)) {
                throw ApiException.badRequest("RECIPE_CYCLE", "A recipe cannot contain itself");
            }
            List<FoodPortion> fp = portionsByFood.getOrDefault(f.getId(), List.of());
            BigDecimal basis = quantityResolver.resolve(f, fp, in.quantity(), in.unit(), in.portionId(),
                    in.weightState()).basisAmount();
            BigDecimal g = basis;
            if (f.getBasisUnit() == BasisUnit.ML) {
                extraWarnings.add("'" + f.getName() + "' is measured in mL; assumed 1 g per mL for the recipe weight.");
            }
            resolved.add(basis);
            grams.add(g);
            calcInputs.add(new RecipeCalculator.Ingredient(f.getId(), f.getName(), f.getBasisQuantity(), basis, g,
                    values.getOrDefault(f.getId(), Map.of()), coverage.get(f.getId())));
        }
        List<String> codes = catalog.all().stream().map(NutrientDefinition::getCode).toList();
        RecipeCalculator.Result result = RecipeCalculator.calculate(calcInputs, cookedWeight, servings, codes);
        List<RecipeNutrientDto> rows = new ArrayList<>();
        for (NutrientDefinition def : catalog.all()) {
            RecipeCalculator.NutrientValue v = result.nutrients().get(def.getCode());
            rows.add(new RecipeNutrientDto(def.getCode(), def.getName(), def.getUnit(), def.getCategory(),
                    v == null ? null : v.per100g().setScale(3, RoundingMode.HALF_UP).stripTrailingZeros(),
                    v == null ? null : v.perServing().setScale(3, RoundingMode.HALF_UP).stripTrailingZeros(),
                    v == null ? null : v.total().setScale(3, RoundingMode.HALF_UP).stripTrailingZeros(),
                    v == null ? null : v.coveragePct()));
        }
        List<String> warnings = new ArrayList<>(result.warnings());
        warnings.addAll(extraWarnings);
        BigDecimal completeness = BigDecimal.valueOf(result.nutrients().size() * 100.0 / codes.size())
                .setScale(2, RoundingMode.HALF_UP);
        RecipeNutrition nutrition = new RecipeNutrition(result.ingredientWeightG(), result.cookedWeightG(),
                result.servings(), result.servingWeightG(), completeness, rows, result.missingNutrients(), warnings);
        return new Computation(nutrition, result, resolved, grams, foodById, stored);
    }

    /** True if the recipe {@code start} (or any recipe nested in it) is {@code target}. */
    private boolean containsRecipe(long start, long target) {
        Deque<Long> stack = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            long current = stack.pop();
            if (current == target) {
                return true;
            }
            if (!seen.add(current) || seen.size() > 50) {
                continue;
            }
            List<Long> nestedFoodIds = ingredients.findByRecipeIdOrderBySortOrderAscIdAsc(current).stream()
                    .map(RecipeIngredient::getFoodId).toList();
            for (Food f : nestedFoodIds.isEmpty() ? List.<Food>of() : foods.findAllByIdIn(nestedFoodIds)) {
                if (f.getRecipeId() != null) {
                    stack.push(f.getRecipeId());
                }
            }
        }
        return false;
    }

    /** Creates or updates the food row that represents the recipe in search and the diary. */
    private void materialize(Recipe r, Computation c) {
        Food food = r.getFoodId() == null ? new Food() : foods.findById(r.getFoodId()).orElseGet(Food::new);
        food.setName(r.getName());
        food.setCategory(r.isSystem() ? "South Indian dishes" : "My recipes");
        food.setFoodType(FoodType.RECIPE);
        food.setPreparationState(PreparationState.COOKED);
        food.setBasisQuantity(BigDecimal.valueOf(100));
        food.setBasisUnit(BasisUnit.G);
        food.setSourceId(sources.findByCode(FoodDataSource.RECIPE).orElseThrow().getId());
        food.setSourceFoodRef(r.isSystem() ? "system:" + r.getSystemKey() : null);
        food.setDataQuality(DataQuality.ESTIMATED);
        food.setOwnerUserId(r.getOwnerUserId());
        food.setRecipeId(r.getId());
        food.setActive(true);
        food.setNotes(r.isSystem()
                ? "Calculated by NutriTrack from a standard home recipe (" + c.result().cookedWeightG().stripTrailingZeros()
                        .toPlainString() + " g cooked). Real dishes vary with proportions and oil."
                : "Calculated from your recipe ingredients.");
        food.setCompletenessPct(c.nutrition().completenessPct());
        foods.saveAndFlush(food);
        r.setFoodId(food.getId());

        foodNutrients.deleteByFoodId(food.getId());
        foodNutrients.flush();
        List<FoodNutrient> rows = new ArrayList<>();
        c.result().nutrients().forEach((code, v) -> rows.add(new FoodNutrient(food.getId(), catalog.byCode(code).getId(),
                v.per100g(), FoodNutrient.CALCULATED, v.coveragePct())));
        foodNutrients.saveAll(rows);

        // Update the two recipe portions in place so diary entries that reference them keep their link.
        BigDecimal servingWeight = c.result().servingWeightG();
        BigDecimal wholeWeight = c.result().cookedWeightG();
        String servingLabel = (r.getServingLabel() == null || r.getServingLabel().isBlank() ? "1 serving"
                : r.getServingLabel()) + " (" + servingWeight.setScale(0, RoundingMode.HALF_UP) + " g)";
        String wholeLabel = WHOLE_RECIPE_LABEL + " (" + wholeWeight.setScale(0, RoundingMode.HALF_UP) + " g)";
        List<FoodPortion> existing = portions.findByFoodIdOrderByIdAsc(food.getId());
        FoodPortion serving = existing.stream().filter(FoodPortion::isDefaultPortion).findFirst().orElse(null);
        FoodPortion whole = existing.stream().filter(p -> p.getLabel().startsWith(WHOLE_RECIPE_LABEL)).findFirst()
                .orElse(null);
        portions.saveAll(List.of(
                upsertPortion(serving, food.getId(), servingLabel, servingWeight, true),
                upsertPortion(whole, food.getId(), wholeLabel, wholeWeight, false)));
    }

    private FoodPortion upsertPortion(FoodPortion existing, Long foodId, String label, BigDecimal grams, boolean dflt) {
        FoodPortion p = existing == null ? new FoodPortion(foodId, label, "SERVING", BigDecimal.ONE, grams, dflt,
                "Calculated from the recipe") : existing;
        p.update(label, grams);
        return p;
    }

    private RecipeDetail detail(Recipe r, long uid, Computation c) {
        List<RecipeIngredient> list = c.stored() != null ? c.stored()
                : ingredients.findByRecipeIdOrderBySortOrderAscIdAsc(r.getId());
        List<IngredientDto> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            RecipeIngredient ing = list.get(i);
            Food f = c.foods().get(ing.getFoodId());
            BigDecimal basis = c.resolved().get(i);
            BigDecimal kcal = null;
            if (f != null) {
                BigDecimal per = foodNutrients.findById(new FoodNutrient.Key(f.getId(), catalog.byCode("ENERGY_KCAL").getId()))
                        .map(FoodNutrient::getAmount).orElse(null);
                kcal = per == null ? null : per.multiply(basis, MC).divide(f.getBasisQuantity(), MC)
                        .setScale(0, RoundingMode.HALF_UP);
            }
            rows.add(new IngredientDto(ing.getId(), ing.getFoodId(), f == null ? "Unknown food" : f.getName(),
                    f == null ? null : f.getDataQuality(), ing.getQuantity().stripTrailingZeros(), ing.getUnit(),
                    ing.getPortionId(), ing.getWeightState(), basis.stripTrailingZeros(),
                    c.grams().get(i).setScale(1, RoundingMode.HALF_UP), kcal, ing.getNote()));
        }
        return new RecipeDetail(r.getId(), r.getName(), r.getDescription(), r.getInstructions(), r.getServings(),
                r.getTotalCookedWeightG(), r.getServingLabel(), r.getFoodId(), r.isSystem(),
                r.getOwnerUserId() != null && r.getOwnerUserId() == uid, rows, c.nutrition());
    }

    private void apply(Recipe r, RecipeRequest req) {
        r.setName(req.name().trim());
        r.setDescription(blank(req.description()));
        r.setInstructions(blank(req.instructions()));
        r.setServings(req.servings());
        r.setTotalCookedWeightG(req.totalCookedWeightG());
        r.setServingLabel(blank(req.servingLabel()));
    }

    private Recipe visible(long id, long uid) {
        Recipe r = recipes.findById(id).orElseThrow(() -> ApiException.notFound("Recipe"));
        if (!r.isSystem() && (r.getOwnerUserId() == null || r.getOwnerUserId() != uid)) {
            throw ApiException.notFound("Recipe");
        }
        return r;
    }

    private Recipe editable(long id, long uid) {
        Recipe r = visible(id, uid);
        if (r.isSystem()) {
            throw ApiException.forbidden("Built-in recipes cannot be changed. Duplicate it to make your own version.");
        }
        return r;
    }

    private static BigDecimal servingWeight(Recipe r) {
        return r.getTotalCookedWeightG() == null ? null
                : r.getTotalCookedWeightG().divide(r.getServings(), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal perServing(BigDecimal per100, BigDecimal servingWeight, int scale) {
        if (per100 == null || servingWeight == null) {
            return null;
        }
        return per100.multiply(servingWeight, MC).divide(BigDecimal.valueOf(100), MC).setScale(scale, RoundingMode.HALF_UP);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
