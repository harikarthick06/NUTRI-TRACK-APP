package com.nutritrack.nutrient;

import com.nutritrack.common.ApiException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** In-memory, read-only view of nutrient definitions (they only change through migrations). */
@Component
public class NutrientCatalog {
    private final NutrientDefinitionRepository repository;
    private volatile List<NutrientDefinition> ordered;
    private volatile Map<String, NutrientDefinition> byCode;
    private volatile Map<Integer, NutrientDefinition> byId;
    private volatile Map<Integer, NutrientDefinition> byUsdaId;

    public NutrientCatalog(NutrientDefinitionRepository repository) {
        this.repository = repository;
    }

    private void ensureLoaded() {
        if (ordered == null) {
            synchronized (this) {
                if (ordered == null) {
                    List<NutrientDefinition> all = repository.findAllByOrderByDisplayOrderAsc();
                    byCode = all.stream().collect(Collectors.toMap(NutrientDefinition::getCode, Function.identity(),
                            (a, b) -> a, LinkedHashMap::new));
                    byId = all.stream().collect(Collectors.toMap(NutrientDefinition::getId, Function.identity()));
                    byUsdaId = all.stream().filter(n -> n.getUsdaNutrientId() != null)
                            .collect(Collectors.toMap(NutrientDefinition::getUsdaNutrientId, Function.identity()));
                    ordered = Collections.unmodifiableList(all);
                }
            }
        }
    }

    public List<NutrientDefinition> all() {
        ensureLoaded();
        return ordered;
    }

    public int count() {
        return all().size();
    }

    public Optional<NutrientDefinition> findByCode(String code) {
        ensureLoaded();
        return Optional.ofNullable(byCode.get(code));
    }

    public NutrientDefinition byCode(String code) {
        return findByCode(code).orElseThrow(() -> ApiException.badRequest("UNKNOWN_NUTRIENT", "Unknown nutrient " + code));
    }

    public NutrientDefinition byId(int id) {
        ensureLoaded();
        NutrientDefinition n = byId.get(id);
        if (n == null) {
            throw new IllegalStateException("Unknown nutrient id " + id);
        }
        return n;
    }

    public Optional<NutrientDefinition> byUsdaId(int usdaId) {
        ensureLoaded();
        return Optional.ofNullable(byUsdaId.get(usdaId));
    }
}
