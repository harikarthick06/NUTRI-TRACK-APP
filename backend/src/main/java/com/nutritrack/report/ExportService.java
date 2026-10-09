package com.nutritrack.report;

import com.nutritrack.nutrition.NutritionSummaryService;
import com.nutritrack.nutrition.NutritionSummaryService.DailyNutrition;
import com.nutritrack.nutrition.NutritionSummaryService.NutrientStatus;
import com.nutritrack.report.ReportRepository.DiaryRow;
import com.nutritrack.report.ReportService.NutrientAverage;
import com.nutritrack.report.ReportService.RangeReport;
import com.nutritrack.security.CurrentUser;
import com.nutritrack.tracking.WeightLog;
import com.nutritrack.tracking.WeightLogRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportService {
    private final NutritionSummaryService nutrition;
    private final ReportService reportService;
    private final ReportRepository reports;
    private final WeightLogRepository weights;

    public ExportService(NutritionSummaryService nutrition, ReportService reportService, ReportRepository reports,
                         WeightLogRepository weights) {
        this.nutrition = nutrition;
        this.reportService = reportService;
        this.reports = reports;
        this.weights = weights;
    }

    @Transactional(readOnly = true)
    public CsvWriter dailyReport(LocalDate date) {
        DailyNutrition day = nutrition.daily(date);
        CsvWriter csv = new CsvWriter();
        csv.row("date", "code", "nutrient", "unit", "category", "amount", "foods_with_data", "foods_logged",
                "coverage", "target", "target_type", "percent_of_target", "upper_limit", "upper_limit_type",
                "upper_limit_applies_to", "status");
        for (NutrientStatus n : day.nutrients()) {
            csv.row(day.date(), n.code(), n.name(), n.unit(), n.category(), n.amount(), n.entriesWithData(),
                    n.entries(), n.coverage(), n.target(), n.targetType(), n.percentOfTarget(), n.upperLimit(),
                    n.upperLimitType(), n.upperLimitAppliesTo(), n.status());
        }
        return csv;
    }

    @Transactional(readOnly = true)
    public CsvWriter diary(LocalDate from, LocalDate to) {
        ReportService.checkRange(from, to);
        CsvWriter csv = new CsvWriter();
        csv.row("date", "logged_at_utc", "meal", "food", "brand", "data_quality", "quantity", "unit", "portion",
                "weight_state", "amount", "amount_unit", "energy_kcal", "protein_g", "carbohydrate_g", "fat_g",
                "fiber_g", "notes");
        for (DiaryRow r : reports.diary(CurrentUser.id(), from, to)) {
            csv.row(r.date(), r.loggedAt(), r.mealType(), r.foodName(), r.brand(), r.dataQuality(), r.quantity(),
                    r.unit(), r.portionLabel(), r.weightState(), r.basisAmount(), r.basisUnit(), round(r.energyKcal(), 0),
                    round(r.proteinG(), 2), round(r.carbsG(), 2), round(r.fatG(), 2), round(r.fiberG(), 2), r.notes());
        }
        return csv;
    }

    @Transactional(readOnly = true)
    public CsvWriter nutrientSummary(LocalDate from, LocalDate to) {
        RangeReport report = reportService.custom(from, to);
        CsvWriter csv = new CsvWriter();
        csv.row("from", "to", "code", "nutrient", "unit", "category", "average_per_logged_day", "days_with_data",
                "logged_days", "target", "target_type", "percent_of_target", "upper_limit", "days_above_upper_limit");
        for (NutrientAverage n : report.nutrients()) {
            csv.row(report.from(), report.to(), n.code(), n.name(), n.unit(), n.category(), n.average(),
                    n.daysWithData(), n.loggedDays(), n.target(), n.targetType(), n.percentOfTarget(),
                    n.upperLimitComparable() ? n.upperLimit() : null,
                    n.upperLimitComparable() ? n.daysAboveUpperLimit() : null);
        }
        return csv;
    }

    @Transactional(readOnly = true)
    public CsvWriter weight(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || from.plusYears(5).isBefore(to)) {
            throw com.nutritrack.common.ApiException.badRequest("INVALID_RANGE",
                    "Choose a range of five years or less, with 'from' on or before 'to'");
        }
        CsvWriter csv = new CsvWriter();
        csv.row("date", "weight_kg", "waist_cm", "body_fat_pct", "note");
        for (WeightLog w : weights.findByUserIdAndLogDateBetweenOrderByLogDateAsc(CurrentUser.id(), from, to)) {
            csv.row(w.getLogDate(), w.getWeightKg(), w.getWaistCm(), w.getBodyFatPct(), w.getNote());
        }
        return csv;
    }

    private static java.math.BigDecimal round(java.math.BigDecimal v, int scale) {
        return v == null ? null : v.setScale(scale, java.math.RoundingMode.HALF_UP);
    }
}
