package com.nutritrack.report;

import com.nutritrack.common.ApiException;
import com.nutritrack.nutrient.NutrientCatalog;
import com.nutritrack.nutrient.NutrientCodes;
import com.nutritrack.nutrient.NutrientDefinition;
import com.nutritrack.nutrition.NutritionAggregationRepository;
import com.nutritrack.nutrition.NutritionAggregationRepository.Total;
import com.nutritrack.nutrition.NutritionSummaryService;
import com.nutritrack.report.ReportRepository.FoodUsage;
import com.nutritrack.report.ReportRepository.MealEnergy;
import com.nutritrack.security.CurrentUser;
import com.nutritrack.target.NutrientTargetService;
import com.nutritrack.target.TargetResolver.Resolved;
import com.nutritrack.tracking.WaterLogRepository;
import com.nutritrack.tracking.WeightLog;
import com.nutritrack.tracking.WeightLogRepository;
import com.nutritrack.user.UserProfile;
import com.nutritrack.user.UserProfileRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Range reports, calendar and trends. Averages are per logged day (a day with at least one diary entry), and for
 * each nutrient only over the logged days that had data for it; the number of such days is always returned.
 */
@Service
public class ReportService {
    static final MathContext MC = MathContext.DECIMAL64;
    public static final int MAX_RANGE_DAYS = 366;
    /** A day counts as "near the calorie target" within this fraction of it. */
    static final BigDecimal CALORIE_TOLERANCE = new BigDecimal("0.10");

    private final NutritionAggregationRepository aggregation;
    private final ReportRepository reports;
    private final NutrientCatalog catalog;
    private final NutrientTargetService targets;
    private final WaterLogRepository water;
    private final WeightLogRepository weights;
    private final UserProfileRepository profiles;
    private final Clock clock;

    public ReportService(NutritionAggregationRepository aggregation, ReportRepository reports, NutrientCatalog catalog,
                         NutrientTargetService targets, WaterLogRepository water, WeightLogRepository weights,
                         UserProfileRepository profiles, Clock clock) {
        this.aggregation = aggregation;
        this.reports = reports;
        this.catalog = catalog;
        this.targets = targets;
        this.water = water;
        this.weights = weights;
        this.profiles = profiles;
        this.clock = clock;
    }

    public record DayRow(LocalDate date, int entries, BigDecimal energyKcal, BigDecimal proteinG, BigDecimal carbsG,
                         BigDecimal fatG, BigDecimal fiberG, int waterMl, BigDecimal weightKg, boolean completed) {
    }

    public record NutrientAverage(String code, String name, String unit, String category, BigDecimal average,
                                  int daysWithData, int loggedDays, BigDecimal target, String targetType,
                                  BigDecimal percentOfTarget, BigDecimal upperLimit, boolean upperLimitComparable,
                                  int daysAboveUpperLimit) {
    }

    /** Share of energy from each macro, from average grams at 4/4/9 kcal per gram. */
    public record MacroSplit(BigDecimal proteinPct, BigDecimal carbsPct, BigDecimal fatPct) {
    }

    public record WeightChange(BigDecimal startKg, LocalDate startDate, BigDecimal endKg, LocalDate endDate,
                               BigDecimal changeKg) {
    }

    public record RangeReport(String period, LocalDate from, LocalDate to, int days, int loggedDays,
                              int completedDays, Integer calorieTargetKcal, int daysNearCalorieTarget,
                              Integer averageWaterMl, Integer waterTargetMl, WeightChange weight,
                              MacroSplit macroSplit, List<NutrientAverage> nutrients, List<MealEnergy> meals,
                              List<FoodUsage> topFoods, List<DayRow> dailyRows, String disclaimer) {
    }

    public record CalendarDay(LocalDate date, int entries, BigDecimal energyKcal, int waterMl, BigDecimal weightKg,
                              boolean completed) {
    }

    public record CalendarMonth(YearMonth month, Integer calorieTargetKcal, List<CalendarDay> days) {
    }

    public record TrendPoint(LocalDate date, BigDecimal amount, int entriesWithData, int entries) {
    }

    public record Trend(String code, String name, String unit, BigDecimal target, BigDecimal upperLimit,
                        LocalDate from, LocalDate to, List<TrendPoint> points) {
    }

    // ---------------------------------------------------------------- periods

    @Transactional(readOnly = true)
    public RangeReport weekly(LocalDate anyDayInWeek) {
        LocalDate d = anyDayInWeek != null ? anyDayInWeek : today(CurrentUser.id());
        LocalDate monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return range("WEEK", monday, monday.plusDays(6));
    }

    @Transactional(readOnly = true)
    public RangeReport monthly(YearMonth month) {
        YearMonth m = month != null ? month : YearMonth.from(today(CurrentUser.id()));
        return range("MONTH", m.atDay(1), m.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public RangeReport custom(LocalDate from, LocalDate to) {
        return range("CUSTOM", from, to);
    }

    public static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("INVALID_RANGE", "Both 'from' and 'to' are required");
        }
        if (from.isAfter(to)) {
            throw ApiException.badRequest("INVALID_RANGE", "'from' must be on or before 'to'");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw ApiException.badRequest("RANGE_TOO_LONG", "Choose a range of up to " + MAX_RANGE_DAYS + " days");
        }
    }

    RangeReport range(String period, LocalDate from, LocalDate to) {
        checkRange(from, to);
        long uid = CurrentUser.id();
        UserProfile profile = profiles.findById(uid).orElse(null);
        Map<LocalDate, Integer> counts = aggregation.entryCounts(uid, from, to);
        Map<LocalDate, Map<String, Total>> totals = aggregation.totalsByDate(uid, from, to);
        Map<LocalDate, Integer> waterByDay = waterByDay(uid, from, to);
        Map<LocalDate, BigDecimal> weightByDay = new HashMap<>();
        List<WeightLog> weightLogs = weights.findByUserIdAndLogDateBetweenOrderByLogDateAsc(uid, from, to);
        weightLogs.forEach(w -> weightByDay.put(w.getLogDate(), w.getWeightKg()));
        Set<LocalDate> completed = reports.completedDays(uid, from, to);
        Map<String, Resolved> resolved = targets.resolveAll(uid);

        List<DayRow> rows = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<String, Total> t = totals.getOrDefault(d, Map.of());
            rows.add(new DayRow(d, counts.getOrDefault(d, 0), amount(t, NutrientCodes.ENERGY, 0),
                    amount(t, NutrientCodes.PROTEIN, 1), amount(t, NutrientCodes.CARBOHYDRATE, 1),
                    amount(t, NutrientCodes.FAT, 1), amount(t, NutrientCodes.FIBER, 1), waterByDay.getOrDefault(d, 0),
                    weightByDay.get(d), completed.contains(d)));
        }
        List<LocalDate> logged = rows.stream().filter(r -> r.entries() > 0).map(DayRow::date).toList();

        List<NutrientAverage> averages = new ArrayList<>();
        for (NutrientDefinition n : catalog.all()) {
            averages.add(average(n, logged, totals, resolved.get(n.getCode())));
        }

        Integer kcalTarget = profile == null ? null : profile.getCalorieTargetKcal();
        int nearTarget = kcalTarget == null ? 0 : (int) rows.stream()
                .filter(r -> r.entries() > 0 && r.energyKcal() != null)
                .filter(r -> withinTolerance(r.energyKcal(), BigDecimal.valueOf(kcalTarget))).count();
        List<Integer> waterDays = rows.stream().map(DayRow::waterMl).filter(w -> w > 0).toList();
        Integer avgWater = waterDays.isEmpty() ? null
                : (int) Math.round(waterDays.stream().mapToInt(Integer::intValue).average().orElse(0));

        WeightChange weight = null;
        if (!weightLogs.isEmpty()) {
            WeightLog first = weightLogs.get(0);
            WeightLog last = weightLogs.get(weightLogs.size() - 1);
            weight = new WeightChange(first.getWeightKg(), first.getLogDate(), last.getWeightKg(), last.getLogDate(),
                    last.getWeightKg().subtract(first.getWeightKg()));
        }
        return new RangeReport(period, from, to, rows.size(), logged.size(), completed.size(), kcalTarget, nearTarget,
                avgWater, profile == null ? null : profile.getWaterTargetMl(), weight, macroSplit(averages), averages,
                reports.energyByMeal(uid, from, to), reports.topFoods(uid, from, to, 10), rows,
                NutrientTargetService.DISCLAIMER);
    }

    static NutrientAverage average(NutrientDefinition n, List<LocalDate> loggedDays,
                                   Map<LocalDate, Map<String, Total>> totals, Resolved r) {
        BigDecimal sum = BigDecimal.ZERO;
        int withData = 0;
        int aboveUpper = 0;
        for (LocalDate d : loggedDays) {
            Total t = totals.getOrDefault(d, Map.of()).get(n.getCode());
            if (t == null || t.entriesWithData() == 0) {
                continue;
            }
            withData++;
            sum = sum.add(t.amount(), MC);
            if (r != null && r.upperLimitComparable() && t.amount().compareTo(r.upperLimit()) > 0) {
                aboveUpper++;
            }
        }
        BigDecimal avg = withData == 0 ? null
                : sum.divide(BigDecimal.valueOf(withData), MC).setScale(n.getUnit().equals("KCAL") ? 0 : 1,
                RoundingMode.HALF_UP);
        BigDecimal target = r == null ? null : r.target();
        BigDecimal pct = avg == null || target == null || target.signum() == 0 ? null
                : avg.multiply(BigDecimal.valueOf(100), MC).divide(target, MC).setScale(0, RoundingMode.HALF_UP);
        return new NutrientAverage(n.getCode(), n.getName(), n.getUnit(), n.getCategory(), avg, withData,
                loggedDays.size(), target, r == null ? null : r.targetType(), pct, r == null ? null : r.upperLimit(),
                r != null && r.upperLimitComparable(), aboveUpper);
    }

    static MacroSplit macroSplit(List<NutrientAverage> averages) {
        Map<String, BigDecimal> avg = new HashMap<>();
        averages.forEach(a -> {
            if (a.average() != null && a.daysWithData() == a.loggedDays()) {
                avg.put(a.code(), a.average());
            }
        });
        BigDecimal p = avg.get(NutrientCodes.PROTEIN);
        BigDecimal c = avg.get(NutrientCodes.CARBOHYDRATE);
        BigDecimal f = avg.get(NutrientCodes.FAT);
        if (p == null || c == null || f == null) {
            return null;
        }
        BigDecimal pk = p.multiply(BigDecimal.valueOf(4));
        BigDecimal ck = c.multiply(BigDecimal.valueOf(4));
        BigDecimal fk = f.multiply(BigDecimal.valueOf(9));
        BigDecimal total = pk.add(ck).add(fk);
        if (total.signum() == 0) {
            return null;
        }
        return new MacroSplit(pctOf(pk, total), pctOf(ck, total), pctOf(fk, total));
    }

    private static BigDecimal pctOf(BigDecimal part, BigDecimal total) {
        return part.multiply(BigDecimal.valueOf(100), MC).divide(total, MC).setScale(1, RoundingMode.HALF_UP);
    }

    static boolean withinTolerance(BigDecimal value, BigDecimal target) {
        BigDecimal diff = value.subtract(target).abs();
        return diff.compareTo(target.multiply(CALORIE_TOLERANCE)) <= 0;
    }

    // ------------------------------------------------------- calendar, trend

    @Transactional(readOnly = true)
    public CalendarMonth calendar(YearMonth month) {
        long uid = CurrentUser.id();
        YearMonth m = month != null ? month : YearMonth.from(today(uid));
        LocalDate from = m.atDay(1);
        LocalDate to = m.atEndOfMonth();
        Map<LocalDate, Integer> counts = aggregation.entryCounts(uid, from, to);
        Map<LocalDate, Map<String, Total>> totals = aggregation.totalsByDate(uid, from, to);
        Map<LocalDate, Integer> waterByDay = waterByDay(uid, from, to);
        Map<LocalDate, BigDecimal> weightByDay = new HashMap<>();
        weights.findByUserIdAndLogDateBetweenOrderByLogDateAsc(uid, from, to)
                .forEach(w -> weightByDay.put(w.getLogDate(), w.getWeightKg()));
        Set<LocalDate> completed = reports.completedDays(uid, from, to);
        List<CalendarDay> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.add(new CalendarDay(d, counts.getOrDefault(d, 0),
                    amount(totals.getOrDefault(d, Map.of()), NutrientCodes.ENERGY, 0), waterByDay.getOrDefault(d, 0),
                    weightByDay.get(d), completed.contains(d)));
        }
        return new CalendarMonth(m, profiles.findById(uid).map(UserProfile::getCalorieTargetKcal).orElse(null), days);
    }

    @Transactional(readOnly = true)
    public Trend trend(String code, LocalDate from, LocalDate to) {
        long uid = CurrentUser.id();
        NutrientDefinition n = catalog.findByCode(code == null ? NutrientCodes.ENERGY : code)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_NUTRIENT", "Unknown nutrient '" + code + "'"));
        LocalDate end = to != null ? to : today(uid);
        LocalDate start = from != null ? from : end.minusDays(29);
        checkRange(start, end);
        Map<LocalDate, Integer> counts = aggregation.entryCounts(uid, start, end);
        Map<LocalDate, Map<String, Total>> totals = aggregation.totalsByDate(uid, start, end);
        List<TrendPoint> points = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Total t = totals.getOrDefault(d, Map.of()).get(n.getCode());
            int entries = counts.getOrDefault(d, 0);
            points.add(new TrendPoint(d, t == null || t.entriesWithData() == 0 ? null
                    : t.amount().setScale(n.getUnit().equals("KCAL") ? 0 : 2, RoundingMode.HALF_UP),
                    t == null ? 0 : t.entriesWithData(), entries));
        }
        Resolved r = targets.resolveAll(uid).get(n.getCode());
        return new Trend(n.getCode(), n.getName(), n.getUnit(), r.target(),
                r.upperLimitComparable() ? r.upperLimit() : null, start, end, points);
    }

    // ---------------------------------------------------------------- helpers

    private Map<LocalDate, Integer> waterByDay(long uid, LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> out = new HashMap<>();
        water.dailyTotals(uid, from, to).forEach(t -> out.put(t.getLogDate(), t.getTotalMl().intValue()));
        return out;
    }

    private static BigDecimal amount(Map<String, Total> totals, String code, int scale) {
        Total t = totals.get(code);
        return t == null || t.entriesWithData() == 0 ? null : t.amount().setScale(scale, RoundingMode.HALF_UP);
    }

    LocalDate today(long uid) {
        return LocalDate.ofInstant(Instant.now(clock), NutritionSummaryService.safeZone(
                profiles.findById(uid).map(UserProfile::getTimezone).orElse(null)));
    }
}
