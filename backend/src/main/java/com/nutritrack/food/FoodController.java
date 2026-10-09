package com.nutritrack.food;

import com.nutritrack.common.PageResponse;
import com.nutritrack.food.FoodDtos.CustomFoodRequest;
import com.nutritrack.food.FoodDtos.FoodDetail;
import com.nutritrack.food.FoodDtos.FoodSummary;
import com.nutritrack.food.FoodDtos.ImportRequest;
import com.nutritrack.food.provider.FoodProviderRegistry;
import com.nutritrack.food.provider.ProviderDtos.ExternalFoodSummary;
import com.nutritrack.food.provider.ProviderDtos.ProviderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/api/foods")
@Validated
@Tag(name = "Foods")
public class FoodController {
    private final FoodService foodService;
    private final FoodProviderRegistry providers;

    public FoodController(FoodService foodService, FoodProviderRegistry providers) {
        this.foodService = foodService;
        this.providers = providers;
    }

    @GetMapping("/search")
    @Operation(summary = "Search local foods (seeded, imported, your custom foods and recipes)")
    public PageResponse<FoodSummary> search(
            @RequestParam(name = "q", required = false) @Size(max = 100) String q,
            @RequestParam(name = "scope", defaultValue = "ALL") FoodService.Scope scope,
            @RequestParam(name = "category", required = false) @Size(max = 100) String category,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return foodService.search(q, scope, category, page, size);
    }

    @GetMapping("/recent")
    @Operation(summary = "Foods you logged most recently")
    public PageResponse<FoodSummary> recent(@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return foodService.search(null, FoodService.Scope.RECENT, null, 0, size);
    }

    @GetMapping("/favorites")
    @Operation(summary = "Your favourite foods")
    public PageResponse<FoodSummary> favorites(@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
                                               @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(100) int size) {
        return foodService.search(null, FoodService.Scope.FAVORITES, null, page, size);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return foodService.categories();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Food details with every tracked nutrient (unknown values are flagged, not zero)")
    public FoodDetail get(@PathVariable long id) {
        return foodService.detail(id);
    }

    @PostMapping("/custom")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a private custom food")
    public FoodDetail createCustom(@Valid @RequestBody CustomFoodRequest request) {
        return foodService.createCustom(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit one of your custom foods")
    public FoodDetail updateCustom(@PathVariable long id, @Valid @RequestBody CustomFoodRequest request) {
        return foodService.updateCustom(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive one of your custom foods (past diary entries keep their values)")
    public void archiveCustom(@PathVariable long id) {
        foodService.archiveCustom(id);
    }

    @PostMapping("/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favorite(@PathVariable long id) {
        foodService.setFavorite(id, true);
    }

    @DeleteMapping("/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(@PathVariable long id) {
        foodService.setFavorite(id, false);
    }

    @GetMapping("/providers")
    @Operation(summary = "External food data providers and whether they are configured")
    public List<ProviderStatus> providerStatus() {
        return providers.statuses();
    }

    @GetMapping("/external/search")
    @Operation(summary = "Search an external provider (USDA_FDC or OPEN_FOOD_FACTS) without storing results")
    public List<ExternalFoodSummary> searchExternal(@RequestParam String provider,
                                                    @RequestParam @Size(max = 100) String q,
                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return foodService.searchExternal(provider, q, limit);
    }

    @PostMapping("/import")
    @Operation(summary = "Import an external food so it can be logged")
    public FoodDetail importFood(@Valid @RequestBody ImportRequest request) {
        return foodService.importExternal(request.provider(), request.externalId());
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Find a packaged food by barcode (local first, then Open Food Facts if enabled)")
    public FoodDetail byBarcode(@PathVariable String barcode) {
        return foodService.byBarcode(barcode);
    }
}
