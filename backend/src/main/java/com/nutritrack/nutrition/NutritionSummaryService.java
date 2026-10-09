package com.nutritrack.nutrition;

import com.nutritrack.nutrient.NutrientCatalog;
import com.nutritrack.nutrient.NutrientDefinition;
import com.nutritrack.nutrition.NutritionAggregationRepository.Total;
import com.nutritrack.security.CurrentUser;
import com.nutritrack.target.NutrientTargetService;
import com.nutritrack.target.TargetResolver.Resolved;
import com.nutritrack.target.TargetResolver.Source;
import com.nutritrack.user.UserProfileRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily nutrient totals compared with the user's targets. Totals only include foods that have data for a nutrient;
 * the data coverage is returned alongside so a low number is never presented as a measured shortfall.
 */
@Service
public class NutritionSummaryService {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    public static final List<String> MICRO_CATEGORIES = List.of("VITAMIN", "MINERAL", "FATTY_ACID", "OTHER");

    private final NutritionAggregationRepository aggregation;
    private final NutrientCatalog catalog;
    private final NutrientTargetService targets;
    private final UserProfileRepository profiles;
    private final Clock clock;

    public NutritionSummaryService(NutritionAggregationRepository aggregation, NutrientCatalog catalog,
                                   NutrientTargetService targets, UserProfileRepository profiles, Clock clock) {
        this.aggregation = aggregation;
        this.catalog = catalog;
        this.targets = targets;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** COMPLETE: every entry had data. PARTIAL: some did. NO_DATA: none did. NO_ENTRIES: nothing logged. */
    public enum DataCoverage { COMPLETE, PARTIAL, NO_DATA, NO_ENTRIES }

    /**
     * Neutral comparison with the target; never a diagnosis. UNKNOWN means no logged food has data for the nutrient,
     * so nothing can be said about it.
     */
    public enum TargetStatus { NO_TARGET, UNKNOWN, BELOW_TARGET, TARGET_REACHED, ABOVE_UPPER_LIMIT }

    public record NutrientStatus(String code, String name, String unit, String category, String formNote,
                                 BigDecimal amount, int entriesWithData, int entries, int entriesPartial,
                                 DataCoverage coverage, BigDecimal target, String targetType, Source targetSource,
                                 BigDecimal percentOfTarget, BigDecimal upperLimit, String upperLimitType,
                                 String upperLimitAppliesTo, boolean upperLimitComparable,
                                 BigDecimal percentOfUpperLimit, TargetStatus status, boolean customTarget,
                                 List<String> notes) {
    }

    public record DailyNutrition(LocalDate date, int entryCount, String disclaimer, List<NutrientStatus> nutrients) {
    }

    public record CategoryCount(String category, int nutrients, int withData) {
    }

    public record MicronutrientView(LocalDate date, int entryCount, String disclaimer, List<CategoryCount> categories,
                                    Map<TargetStatus, Integer> statusCounts, List<NutrientStatus> nutrients) {
    }

    @Transactional(readOnly = true)
    public DailyNutrition daily(LocalDate date) {
        long uid = CurrentUser.id();
        LocalDate d = date != null ? date : today(uid);
        int entries = aggregation.entryCounts(uid, d, d).getOrDefault(d, 0);
        Map<String, Total> totals = aggregation.totalsByDate(uid, d, d).getOrDefault(d, Map.of());
        Map<String, Resolved> resolved = targets.resolveAll(uid);
        List<NutrientStatus> list = new ArrayList<>();
        for (NutrientDefinition n : catalog.all()) {
            list.add(status(n, totals.get(n.getCode()), entries, resolved.get(n.getCode())));
        }
        return new DailyNutrition(d, entries, NutrientTargetService.DISCLAIMER, list);
    }

    @Transactional(readOnly = true)
    public MicronutrientView micronutrients(LocalDate date, String category, String query, TargetStatus status) {
        DailyNutrition day = daily(date);
        List<NutrientStatus> micros = day.nutrients().stream()
                .filter(n -> MICRO_CATEGORIES.contains(n.category()) || "CHOLESTEROL".equals(n.code()))
                .toList();
        List<CategoryCount> counts = new ArrayList<>();
        for (String c : MICRO_CATEGORIES) {
            List<NutrientStatus> inCat = micros.stream().filter(n -> n.category().equals(c)).toList();
            counts.add(new CategoryCount(c, inCat.size(), (int) inCat.stream().filter(n -> n.amount() != null).count()));
        }
        String q = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        List<NutrientStatus> filtered = micros.stream()
                .filter(n -> category == null || category.isBlank() || n.category().equalsIgnoreCase(category))
                .filter(n -> q == null || n.name().toLowerCase(Locale.ROOT).contains(q)
                        || n.code().toLowerCase(Locale.ROOT).contains(q))
                .toList();
        Map<TargetStatus, Integer> statusCounts = new LinkedHashMap<>();
        for (TargetStatus s : TargetStatus.values()) {
            statusCounts.put(s, (int) filtered.stream().filter(n -> n.status() == s).count());
        }
        if (status != null) {
            filtered = filtered.stream().filter(n -> n.status() == status).toList();
        }
        return new MicronutrientView(day.date(), day.entryCount(), day.disclaimer(), counts, statusCounts, filtered);
    }

    static NutrientStatus status(NutrientDefinition n, Total total, int entries, Resolved r) {
        int withData = total == null ? 0 : total.entriesWithData();
        int partial = total == null ? 0 : total.entriesPartial();
        BigDecimal amount = withData == 0 ? null : total.amount().setScale(scale(n.getUnit()), RoundingMode.HALF_UP);
        DataCoverage coverage = entries == 0 ? DataCoverage.NO_ENTRIES
                : withData == 0 ? DataCoverage.NO_DATA
                : withData < entries || partial > 0 ? DataCoverage.PARTIAL : DataCoverage.COMPLETE;
        BigDecimal pctTarget = pct(amount, r.target());
        BigDecimal pctUpper = r.upperLimitComparable() ? pct(amount, r.upperLimit()) : null;
        TargetStatus status;
        if (amount != null && r.upperLimitComparable() && amount.compareTo(r.upperLimit()) > 0) {
            status = TargetStatus.ABOVE_UPPER_LIMIT;
        } else if (r.target() == null) {
            status = TargetStatus.NO_TARGET;
        } else if (amount == null) {
            status = TargetStatus.UNKNOWN;
        } else if (amount != null && amount.compareTo(r.target()) >= 0) {
            status = TargetStatus.TARGET_REACHED;
        } else {
            status = TargetStatus.BELOW_TARGET;
        }
        List<String> notes = new ArrayList<>(r.notes());
        if (coverage == DataCoverage.PARTIAL) {
            notes.add("Only " + withData + " of " + entries + " logged foods have data for this nutrient, "
                    + "so the real total is probably higher");
        }
        if (r.upperLimit() != null && !r.upperLimitComparable()) {
            notes.add(appliesToNote(r.upperLimitAppliesTo()));
        }
        return new NutrientStatus(n.getCode(), n.getName(), n.getUnit(), n.getCategory(), n.getFormNote(), amount,
                withData, entries, partial, coverage, r.target(), r.targetType(), r.targetSource(), pctTarget,
                r.upperLimit(), r.upperLimitType(), r.upperLimitAppliesTo(), r.upperLimitComparable(), pctUpper,
                status, r.customized(), notes);
    }

    private static String appliesToNote(String appliesTo) {
        return switch (appliesTo == null ? "" : appliesTo) {
            case "SUPPLEMENTAL_ONLY" -> "The upper limit covers supplements and fortified foods only, so it is not "
                    + "compared with food totals";
            case "PREFORMED_ONLY" -> "The upper limit covers preformed vitamin A (retinol) only, not carotenoids from "
                    + "plants, so it is not compared with this total";
            default -> "The upper limit covers only part of intake, so it is not compared with this total";
        };
    }

    private static BigDecimal pct(BigDecimal amount, BigDecimal of) {
        if (amount == null || of == null || of.signum() == 0) {
            return null;
        }
        return amount.multiply(HUNDRED, MC).divide(of, MC).setScale(0, RoundingMode.HALF_UP);
    }

    private static int scale(String unit) {
        return switch (unit) {
            case "KCAL" -> 0;
            case "G" -> 1;
            case "MG" -> 1;
            default -> 1;
        };
    }

    private LocalDate today(long uid) {
        ZoneId zone = profiles.findById(uid).map(p -> safeZone(p.getTimezone())).orElse(ZoneId.of("Asia/Kolkata"));
        return LocalDate.now(clock.withZone(zone));
    }

    public static ZoneId safeZone(String tz) {
        try {
            return tz == null ? ZoneId.of("Asia/Kolkata") : ZoneId.of(tz);
        } catch (RuntimeException e) {
            return ZoneId.of("Asia/Kolkata");
        }
    }
}
