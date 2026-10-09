package com.nutritrack.food;

import com.nutritrack.common.ApiException;
import com.nutritrack.common.Enums.DataQuality;
import com.nutritrack.common.Enums.FoodType;
import com.nutritrack.common.Enums.PreparationState;
import com.nutritrack.common.PageResponse;
import com.nutritrack.food.FoodDtos.CustomFoodRequest;
import com.nutritrack.food.FoodDtos.FoodDetail;
import com.nutritrack.food.FoodDtos.FoodNutrientDto;
import com.nutritrack.food.FoodDtos.FoodSummary;
import com.nutritrack.food.FoodDtos.PortionDto;
import com.nutritrack.food.FoodDtos.PortionInput;
import com.nutritrack.food.FoodDtos.SourceDto;
import com.nutritrack.food.provider.FoodDataProvider;
import com.nutritrack.food.provider.FoodProviderRegistry;
import com.nutritrack.food.provider.ProviderDtos.ExternalFoodSummary;
import com.nutritrack.food.provider.ProviderDtos.ImportedFood;
import com.nutritrack.nutrient.NutrientCatalog;
import com.nutritrack.nutrient.NutrientCodes;
import com.nutritrack.nutrient.NutrientDefinition;
import com.nutritrack.nutrition.QuantityResolver;
import com.nutritrack.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodService {
    public enum Scope { ALL, MINE, FAVORITES, RECENT }

    private static final List<String> SUMMARY_NUTRIENTS = List.of(NutrientCodes.ENERGY, NutrientCodes.PROTEIN,
            NutrientCodes.CARBOHYDRATE, NutrientCodes.FAT);

    private final FoodRepository foods;
    private final FoodNutrientRepository foodNutrients;
    private final FoodPortionRepository portions;
    private final FoodDataSourceRepository sources;
    private final NutrientCatalog catalog;
    private final EnergyValidator energyValidator;
    private final FoodProviderRegistry providers;
    private final NamedParameterJdbcTemplate jdbc;

    public FoodService(FoodRepository foods, FoodNutrientRepository foodNutrients, FoodPortionRepository portions,
                       FoodDataSourceRepository sources, NutrientCatalog catalog, EnergyValidator energyValidator,
                       FoodProviderRegistry providers, NamedParameterJdbcTemplate jdbc) {
        this.foods = foods;
        this.foodNutrients = foodNutrients;
        this.portions = portions;
        this.sources = sources;
        this.catalog = catalog;
        this.energyValidator = energyValidator;
        this.providers = providers;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ search

    @Transactional(readOnly = true)
    public PageResponse<FoodSummary> search(String query, Scope scope, String category, int page, int size) {
        long uid = CurrentUser.id();
        MapSqlParameterSource params = new MapSqlParameterSource("uid", uid);
        StringBuilder from = new StringBuilder(" FROM foods f");
        StringBuilder where = new StringBuilder(
                " WHERE f.is_active = TRUE AND (f.owner_user_id IS NULL OR f.owner_user_id = :uid)");
        String order;
        switch (scope) {
            case FAVORITES -> from.append(" JOIN user_favorite_foods uf ON uf.food_id = f.id AND uf.user_id = :uid");
            case RECENT -> from.append(" JOIN (SELECT food_id, MAX(logged_at) AS last_used FROM food_diary_entries"
                    + " WHERE user_id = :uid GROUP BY food_id) r ON r.food_id = f.id");
            case MINE -> where.append(" AND f.owner_user_id = :uid");
            case ALL -> {
            }
        }
        List<String> tokens = tokenize(query);
        for (int i = 0; i < tokens.size(); i++) {
            String p = "t" + i;
            params.addValue(p, "%" + escapeLike(tokens.get(i)) + "%");
            where.append(" AND (f.name LIKE :").append(p).append(" OR f.local_names LIKE :").append(p)
                    .append(" OR f.brand LIKE :").append(p).append(" OR f.category LIKE :").append(p).append(")");
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND f.category = :category");
            params.addValue("category", category.trim());
        }
        if (scope == Scope.RECENT) {
            order = " ORDER BY r.last_used DESC, f.id";
        } else if (!tokens.isEmpty()) {
            String phrase = String.join(" ", tokens);
            params.addValue("prefix", escapeLike(phrase) + "%");
            params.addValue("word", "% " + escapeLike(phrase) + "%");
            params.addValue("first", escapeLike(tokens.get(0)) + "%");
            StringBuilder wordHits = new StringBuilder("0");
            for (int i = 0; i < tokens.size(); i++) {
                String w = tokens.get(i).replaceAll("[^\\p{L}\\p{N}]", "");
                if (!w.isEmpty()) {
                    params.addValue("w" + i, "\\b" + w + "\\b");
                    wordHits.append(" + CASE WHEN REGEXP_LIKE(f.name, :w").append(i).append(", 'i') THEN 1 ELSE 0 END");
                }
            }
            order = " ORDER BY (" + wordHits + ") DESC, CASE WHEN f.name LIKE :prefix THEN 0 WHEN f.name LIKE :word THEN 1"
                    + " WHEN f.name LIKE :first THEN 2 WHEN f.local_names LIKE :t0 THEN 3 ELSE 4 END,"
                    + " CASE WHEN f.owner_user_id IS NULL THEN 1 ELSE 0 END, f.completeness_pct DESC,"
                    + " CHAR_LENGTH(f.name), f.name";
        } else {
            order = " ORDER BY CASE WHEN f.owner_user_id IS NULL THEN 1 ELSE 0 END, f.name";
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<Long> ids = jdbc.queryForList("SELECT f.id" + from + where + order + " LIMIT :limit OFFSET :offset",
                params, Long.class);
        return PageResponse.of(summaries(ids, uid), page, size, total == null ? 0 : total);
    }

    /** Builds summaries for the given food ids, preserving their order, with one query per related table. */
    @Transactional(readOnly = true)
    public List<FoodSummary> summaries(List<Long> ids, long uid) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Food> byId = foods.findAllByIdIn(ids).stream().collect(Collectors.toMap(Food::getId, Function.identity()));
        Map<Long, Map<String, BigDecimal>> macros = new HashMap<>();
        jdbc.query("SELECT fn.food_id, n.code, fn.amount FROM food_nutrients fn JOIN nutrient_definitions n"
                        + " ON n.id = fn.nutrient_id WHERE fn.food_id IN (:ids) AND n.code IN (:codes)",
                new MapSqlParameterSource("ids", ids).addValue("codes", SUMMARY_NUTRIENTS),
                rs -> {
                    macros.computeIfAbsent(rs.getLong(1), k -> new HashMap<>()).put(rs.getString(2), rs.getBigDecimal(3));
                });
        Map<Long, PortionDto> defaultPortions = new HashMap<>();
        for (FoodPortion p : portions.findByFoodIdIn(ids)) {
            // The flagged default wins; otherwise the first portion seen is used.
            if (p.isDefaultPortion() || !defaultPortions.containsKey(p.getFoodId())) {
                defaultPortions.put(p.getFoodId(), toDto(p));
            }
        }
        Set<Long> favorites = favoriteIds(uid, ids);
        Map<Integer, String> sourceCodes = sources.findAll().stream()
                .collect(Collectors.toMap(FoodDataSource::getId, FoodDataSource::getCode));
        List<FoodSummary> out = new ArrayList<>();
        for (Long id : ids) {
            Food f = byId.get(id);
            if (f == null) {
                continue;
            }
            Map<String, BigDecimal> m = macros.getOrDefault(id, Map.of());
            out.add(new FoodSummary(f.getId(), f.getName(), f.getBrand(), f.getCategory(), f.getFoodType(),
                    f.getPreparationState(), f.getBasisQuantity(), f.getBasisUnit(), f.getDataQuality(),
                    sourceCodes.get(f.getSourceId()), f.getCompletenessPct(), round(m.get(NutrientCodes.ENERGY)),
                    round(m.get(NutrientCodes.PROTEIN)), round(m.get(NutrientCodes.CARBOHYDRATE)),
                    round(m.get(NutrientCodes.FAT)), defaultPortions.get(id), favorites.contains(id),
                    f.getOwnerUserId() != null && f.getOwnerUserId() == uid));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return jdbc.queryForList("SELECT DISTINCT category FROM foods WHERE is_active = TRUE AND category IS NOT NULL"
                        + " AND (owner_user_id IS NULL OR owner_user_id = :uid) ORDER BY category",
                new MapSqlParameterSource("uid", CurrentUser.id()), String.class);
    }

    // ------------------------------------------------------------------ detail

    @Transactional(readOnly = true)
    public FoodDetail detail(long id) {
        long uid = CurrentUser.id();
        Food f = visibleFood(id, uid);
        return toDetail(f, uid);
    }

    /** Loads a food the current user may see. Other users' private foods are reported as not found. */
    public Food visibleFood(long id, long uid) {
        Food f = foods.findById(id).orElseThrow(() -> ApiException.notFound("Food"));
        if (f.getOwnerUserId() != null && f.getOwnerUserId() != uid) {
            throw ApiException.notFound("Food");
        }
        return f;
    }

    FoodDetail toDetail(Food f, long uid) {
        Map<Integer, FoodNutrient> values = foodNutrients.findByFoodId(f.getId()).stream()
                .collect(Collectors.toMap(FoodNutrient::getNutrientId, Function.identity()));
        List<FoodNutrientDto> rows = new ArrayList<>();
        Map<String, BigDecimal> byCode = new HashMap<>();
        for (NutrientDefinition def : catalog.all()) {
            FoodNutrient v = values.get(def.getId());
            if (v != null) {
                byCode.put(def.getCode(), v.getAmount());
            }
            rows.add(new FoodNutrientDto(def.getCode(), def.getName(), def.getUnit(), def.getCategory(),
                    def.getFormNote(), v == null ? null : v.getAmount().stripTrailingZeros(), v != null,
                    v == null ? null : v.getValueOrigin(), v == null ? null : v.getCoveragePct()));
        }
        FoodDataSource src = sources.findById(f.getSourceId()).orElseThrow();
        List<FoodPortion> foodPortions = portions.findByFoodIdOrderByIdAsc(f.getId());
        List<PortionDto> portionDtos = foodPortions.stream().map(FoodService::toDto).toList();
        boolean favorite = !favoriteIds(uid, List.of(f.getId())).isEmpty();
        return new FoodDetail(f.getId(), f.getName(), f.getBrand(), f.getCategory(), f.getFoodType(),
                f.getPreparationState(), f.getBasisQuantity(), f.getBasisUnit(), f.getDataQuality(),
                new SourceDto(src.getCode(), src.getName(), src.getUrl(), src.getVersion(), src.getLicenseNote()),
                f.getSourceFoodRef(), f.getCompletenessPct(), f.getLocalNames(), f.getBarcode(),
                f.getEdiblePortionPct(), f.getCookedYieldFactor(), f.getCookedYieldNote(), f.getNotes(),
                f.getRecipeId(), rows, portionDtos, QuantityResolver.supportedUnits(f, foodPortions),
                energyValidator.check(byCode), favorite,
                f.getOwnerUserId() != null && f.getOwnerUserId() == uid);
    }

    // ------------------------------------------------------------------ custom foods

    @Transactional
    public FoodDetail createCustom(CustomFoodRequest req) {
        long uid = CurrentUser.id();
        Food f = new Food();
        f.setOwnerUserId(uid);
        f.setFoodType(FoodType.CUSTOM);
        f.setDataQuality(DataQuality.USER_ENTERED);
        f.setSourceId(sourceId(FoodDataSource.USER));
        applyCustom(f, req);
        foods.saveAndFlush(f);
        writeNutrients(f, req.nutrients(), FoodNutrient.USER_ENTERED);
        writePortions(f.getId(), req.portions(), "Entered by user");
        return toDetail(f, uid);
    }

    @Transactional
    public FoodDetail updateCustom(long id, CustomFoodRequest req) {
        long uid = CurrentUser.id();
        Food f = editableCustomFood(id, uid);
        applyCustom(f, req);
        foodNutrients.deleteByFoodId(f.getId());
        foodNutrients.flush();
        writeNutrients(f, req.nutrients(), FoodNutrient.USER_ENTERED);
        portions.deleteByFoodId(f.getId());
        writePortions(f.getId(), req.portions(), "Entered by user");
        return toDetail(f, uid);
    }

    /** Custom foods are archived rather than deleted so that past diary entries keep their nutrients. */
    @Transactional
    public void archiveCustom(long id) {
        long uid = CurrentUser.id();
        Food f = editableCustomFood(id, uid);
        f.setActive(false);
    }

    private Food editableCustomFood(long id, long uid) {
        Food f = visibleFood(id, uid);
        if (f.getOwnerUserId() == null) {
            throw ApiException.forbidden("Only your own custom foods can be edited");
        }
        if (f.getFoodType() != FoodType.CUSTOM) {
            throw ApiException.badRequest("NOT_A_CUSTOM_FOOD", "Edit this food through its recipe instead");
        }
        if (!f.isActive()) {
            throw ApiException.notFound("Food");
        }
        return f;
    }

    private void applyCustom(Food f, CustomFoodRequest req) {
        f.setName(req.name().trim());
        f.setBrand(blankToNull(req.brand()));
        f.setCategory(blankToNull(req.category()) == null ? "Custom foods" : req.category().trim());
        f.setPreparationState(req.preparationState() == null ? PreparationState.NOT_APPLICABLE : req.preparationState());
        f.setBasisQuantity(req.basisQuantity());
        f.setBasisUnit(req.basisUnit());
        f.setBarcode(blankToNull(req.barcode()));
        f.setNotes(blankToNull(req.notes()));
        for (String code : req.nutrients().keySet()) {
            catalog.byCode(code);
        }
        if (!req.nutrients().containsKey(NutrientCodes.ENERGY)) {
            throw ApiException.badRequest("ENERGY_REQUIRED", "Energy (kcal) is required for a custom food");
        }
    }

    // ------------------------------------------------------------------ favourites

    @Transactional
    public void setFavorite(long foodId, boolean favorite) {
        long uid = CurrentUser.id();
        visibleFood(foodId, uid);
        MapSqlParameterSource p = new MapSqlParameterSource("uid", uid).addValue("fid", foodId);
        if (favorite) {
            jdbc.update("INSERT IGNORE INTO user_favorite_foods (user_id, food_id) VALUES (:uid, :fid)", p);
        } else {
            jdbc.update("DELETE FROM user_favorite_foods WHERE user_id = :uid AND food_id = :fid", p);
        }
    }

    private Set<Long> favoriteIds(long uid, List<Long> ids) {
        return new HashSet<>(jdbc.queryForList("SELECT food_id FROM user_favorite_foods WHERE user_id = :uid"
                + " AND food_id IN (:ids)", new MapSqlParameterSource("uid", uid).addValue("ids", ids), Long.class));
    }

    // ------------------------------------------------------------------ external providers

    public List<ExternalFoodSummary> searchExternal(String provider, String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            throw ApiException.badRequest("QUERY_TOO_SHORT", "Enter at least 2 characters");
        }
        return providers.enabled(provider).search(query.trim(), limit);
    }

    /** Imports an external food once; later imports of the same id return the stored copy. */
    @Transactional
    public FoodDetail importExternal(String providerCode, String externalId) {
        long uid = CurrentUser.id();
        FoodDataProvider provider = providers.enabled(providerCode);
        int sourceId = sourceId(provider.sourceCode());
        var existing = foods.findBySourceIdAndSourceFoodRef(sourceId, externalId);
        if (existing.isPresent()) {
            return toDetail(existing.get(), uid);
        }
        ImportedFood imported = provider.fetch(externalId)
                .orElseThrow(() -> ApiException.notFound(provider.displayName() + " food"));
        return toDetail(saveImported(imported, sourceId), uid);
    }

    @Transactional
    public FoodDetail byBarcode(String barcode) {
        long uid = CurrentUser.id();
        if (!barcode.matches("\\d{4,14}")) {
            throw ApiException.badRequest("INVALID_BARCODE", "Barcodes contain 4 to 14 digits");
        }
        List<Long> local = jdbc.queryForList("SELECT id FROM foods WHERE barcode = :b AND is_active = TRUE"
                        + " AND (owner_user_id IS NULL OR owner_user_id = :uid) ORDER BY owner_user_id DESC LIMIT 1",
                new MapSqlParameterSource("b", barcode).addValue("uid", uid), Long.class);
        if (!local.isEmpty()) {
            return detail(local.get(0));
        }
        return importExternal(FoodDataSource.OPEN_FOOD_FACTS, barcode);
    }

    Food saveImported(ImportedFood imported, int sourceId) {
        Food f = new Food();
        f.setName(imported.name());
        f.setBrand(imported.brand());
        f.setCategory(imported.category());
        f.setFoodType(imported.foodType());
        f.setPreparationState(imported.preparationState());
        f.setBasisQuantity(imported.basisQuantity());
        f.setBasisUnit(imported.basisUnit());
        f.setSourceId(sourceId);
        f.setSourceFoodRef(imported.externalId());
        f.setDataQuality(DataQuality.EXTERNAL);
        f.setBarcode(imported.barcode() != null && imported.barcode().length() <= 32 ? imported.barcode() : null);
        f.setNotes(imported.notes());
        foods.saveAndFlush(f);
        writeNutrients(f, imported.nutrients(), FoodNutrient.REPORTED);
        List<PortionInput> inputs = imported.portions().stream()
                .map(p -> new PortionInput(p.label(), p.unit(), p.unitQuantity(), p.basisAmount())).toList();
        writePortions(f.getId(), inputs, "From " + imported.sourceCode());
        return f;
    }

    // ------------------------------------------------------------------ helpers

    void writeNutrients(Food f, Map<String, BigDecimal> input, String origin) {
        Map<String, BigDecimal> values = new LinkedHashMap<>(input);
        Map<String, String> origins = new HashMap<>();
        values.keySet().forEach(k -> origins.put(k, origin));
        NutrientDerivations.fill(values, origins);
        List<FoodNutrient> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : values.entrySet()) {
            NutrientDefinition def = catalog.byCode(e.getKey());
            rows.add(new FoodNutrient(f.getId(), def.getId(), e.getValue().setScale(6, RoundingMode.HALF_UP),
                    origins.get(e.getKey()), null));
        }
        foodNutrients.saveAll(rows);
        f.setCompletenessPct(completeness(rows.size()));
    }

    private void writePortions(Long foodId, List<PortionInput> inputs, String note) {
        if (inputs == null) {
            return;
        }
        Set<String> labels = new HashSet<>();
        boolean first = true;
        for (PortionInput p : inputs) {
            if (!labels.add(p.label().trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            portions.save(new FoodPortion(foodId, p.label().trim(), p.unit(), p.unitQuantity(), p.basisAmount(),
                    first, note));
            first = false;
        }
    }

    BigDecimal completeness(int known) {
        return BigDecimal.valueOf(known * 100.0 / catalog.count()).setScale(2, RoundingMode.HALF_UP);
    }

    int sourceId(String code) {
        return sources.findByCode(code).map(FoodDataSource::getId)
                .orElseThrow(() -> new IllegalStateException("Missing food data source " + code));
    }

    static PortionDto toDto(FoodPortion p) {
        return new PortionDto(p.getId(), p.getLabel(), p.getUnit(), p.getUnitQuantity().stripTrailingZeros(),
                p.getBasisAmount().stripTrailingZeros(), p.isDefaultPortion(), p.getSourceNote());
    }

    static List<String> tokenize(String query) {
        if (query == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String t : query.trim().toLowerCase(Locale.ROOT).split("[\\s,]+")) {
            if (!t.isBlank() && out.size() < 6) {
                out.add(t.length() > 40 ? t.substring(0, 40) : t);
            }
        }
        return out;
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static BigDecimal round(BigDecimal v) {
        return v == null ? null : v.setScale(1, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
