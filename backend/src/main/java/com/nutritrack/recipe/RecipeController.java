package com.nutritrack.recipe;

import com.nutritrack.diary.DiaryDtos.EntryDto;
import com.nutritrack.recipe.RecipeDtos.LogRecipeRequest;
import com.nutritrack.recipe.RecipeDtos.RecipeDetail;
import com.nutritrack.recipe.RecipeDtos.RecipeNutrition;
import com.nutritrack.recipe.RecipeDtos.RecipeRequest;
import com.nutritrack.recipe.RecipeDtos.RecipeSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/recipes")
@Tag(name = "Recipes")
public class RecipeController {
    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    @Operation(summary = "Your recipes and the built-in South Indian recipes")
    public List<RecipeSummary> list() {
        return recipeService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Recipe with ingredients and calculated nutrition")
    public RecipeDetail get(@PathVariable long id) {
        return recipeService.get(id);
    }

    @GetMapping("/{id}/nutrition")
    @Operation(summary = "Calculated nutrition of a saved recipe (per 100 g, per serving and total)")
    public RecipeNutrition nutrition(@PathVariable long id) {
        return recipeService.get(id).nutrition();
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate nutrition for an unsaved recipe (recipe builder preview)")
    public RecipeNutrition calculate(@Valid @RequestBody RecipeRequest request) {
        return recipeService.preview(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeDetail create(@Valid @RequestBody RecipeRequest request) {
        return recipeService.create(request);
    }

    @PutMapping("/{id}")
    public RecipeDetail update(@PathVariable long id, @Valid @RequestBody RecipeRequest request) {
        return recipeService.update(id, request);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Copy a recipe (e.g. a built-in one) into an editable recipe of your own")
    public RecipeDetail duplicate(@PathVariable long id) {
        return recipeService.duplicate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        recipeService.delete(id);
    }

    @PostMapping("/{id}/log")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Log servings of the recipe, or the whole recipe, to the diary")
    public EntryDto log(@PathVariable long id, @Valid @RequestBody LogRecipeRequest request) {
        return recipeService.log(id, request);
    }
}
