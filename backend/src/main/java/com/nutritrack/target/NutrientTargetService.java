package com.nutritrack.target;

import com.nutritrack.common.ApiException;
import com.nutritrack.nutrient.NutrientCatalog;
import com.nutritrack.nutrient.NutrientDefinition;
import com.nutritrack.security.CurrentUser;
import com.nutritrack.target.ReferenceValueRepository.ReferenceRow;
import com.nutritrack.target.TargetResolver.ProfileInput;
import com.nutritrack.target.TargetResolver.Resolved;
import com.nutritrack.user.UserProfile;
import com.nutritrack.user.UserProfileRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NutrientTargetService {
    public static final String DISCLAIMER = "Reference values are population averages for healthy people, not "
            + "personal prescriptions. Talk to a doctor or registered dietitian about your own needs.";

    private final NutrientCatalog catalog;
    private final ReferenceValueRepository references;
    private final UserNutrientTargetRepository overrides;
    private final UserProfileRepository profiles;

    public NutrientTargetService(NutrientCatalog catalog, ReferenceValueRepository references,
                                 UserNutrientTargetRepository overrides, UserProfileRepository profiles) {
        this.catalog = catalog;
        this.references = references;
        this.overrides = overrides;
        this.profiles = profiles;
    }

    public record NutrientTargetDto(String code, String name, String unit, String category, String formNote,
                                    BigDecimal target, String targetType, TargetResolver.Source targetSource,
                                    BigDecimal upperLimit, String upperLimitType, String upperLimitAppliesTo,
                                    boolean upperLimitComparable, TargetResolver.Source upperLimitSource,
                                    boolean customized, List<String> notes) {
    }

    public record TargetsResponse(String referenceSystem, Integer age, String sex, String disclaimer,
                                  List<NutrientTargetDto> nutrients) {
    }

    public record TargetUpdate(@NotBlank @Size(max = 40) String code,
                               @DecimalMin("0") @DecimalMax("1000000") BigDecimal target,
                               @DecimalMin("0") @DecimalMax("1000000") BigDecimal upperLimit) {
    }

    public record TargetUpdateRequest(@NotEmpty @Size(max = 60) List<@Valid TargetUpdate> targets) {
    }

    /** Resolved targets for every nutrient, keyed by code in catalog order. */
    @Transactional(readOnly = true)
    public Map<String, Resolved> resolveAll(long userId) {
        UserProfile profile = profiles.findById(userId).orElse(null);
        String system = profile == null ? "NASEM_DRI" : profile.getReferenceSystem();
        ProfileInput input = input(profile);
        Map<String, List<ReferenceRow>> rows = TargetResolver.byNutrient(references.forSystem(system));
        Map<Integer, UserNutrientTarget> own = new HashMap<>();
        overrides.findByUserId(userId).forEach(t -> own.put(t.getNutrientId(), t));
        Map<String, Resolved> out = new LinkedHashMap<>();
        for (NutrientDefinition n : catalog.all()) {
            UserNutrientTarget o = own.get(n.getId());
            out.put(n.getCode(), TargetResolver.resolve(n.getCode(), input, rows.getOrDefault(n.getCode(), List.of()),
                    o == null ? null : new TargetResolver.Override(o.getTargetAmount(), o.getUpperLimit())));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public TargetsResponse list() {
        long uid = CurrentUser.id();
        UserProfile profile = profiles.findById(uid).orElse(null);
        Map<String, Resolved> resolved = resolveAll(uid);
        List<NutrientTargetDto> dtos = catalog.all().stream().map(n -> dto(n, resolved.get(n.getCode()))).toList();
        return new TargetsResponse(profile == null ? "NASEM_DRI" : profile.getReferenceSystem(),
                profile == null ? null : profile.getAge(),
                profile == null || profile.getSex() == null ? null : profile.getSex().name(), DISCLAIMER, dtos);
    }

    @Transactional
    public TargetsResponse update(TargetUpdateRequest request) {
        long uid = CurrentUser.id();
        for (TargetUpdate u : request.targets()) {
            NutrientDefinition n = catalog.findByCode(u.code())
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_NUTRIENT", "Unknown nutrient '" + u.code() + "'"));
            if (u.target() != null && u.upperLimit() != null && u.upperLimit().compareTo(u.target()) < 0) {
                throw ApiException.badRequest("UPPER_BELOW_TARGET",
                        "The upper limit for " + n.getName() + " is lower than its target");
            }
            UserNutrientTarget t = overrides.findByUserIdAndNutrientId(uid, n.getId()).orElse(null);
            if (u.target() == null && u.upperLimit() == null) {
                if (t != null) {
                    overrides.delete(t);
                }
                continue;
            }
            if (t == null) {
                t = new UserNutrientTarget(uid, n.getId());
            }
            t.setTargetAmount(u.target());
            t.setUpperLimit(u.upperLimit());
            overrides.save(t);
        }
        overrides.flush();
        return list();
    }

    @Transactional
    public TargetsResponse reset(String code) {
        long uid = CurrentUser.id();
        if (code == null) {
            overrides.deleteAllForUser(uid);
        } else {
            NutrientDefinition n = catalog.findByCode(code).orElseThrow(() -> ApiException.notFound("Nutrient"));
            overrides.findByUserIdAndNutrientId(uid, n.getId()).ifPresent(overrides::delete);
        }
        overrides.flush();
        return list();
    }

    static ProfileInput input(UserProfile p) {
        if (p == null) {
            return new ProfileInput(null, null, null, null, null, null, null);
        }
        return new ProfileInput(p.getAge(), p.getSex(), p.getCalorieTargetKcal(), p.getProteinTargetG(),
                p.getCarbsTargetG(), p.getFatTargetG(), p.getFiberTargetG());
    }

    static NutrientTargetDto dto(NutrientDefinition n, Resolved r) {
        return new NutrientTargetDto(n.getCode(), n.getName(), n.getUnit(), n.getCategory(), n.getFormNote(),
                r.target(), r.targetType(), r.targetSource(), r.upperLimit(), r.upperLimitType(),
                r.upperLimitAppliesTo(), r.upperLimitComparable(), r.upperLimitSource(), r.customized(), r.notes());
    }
}
