package com.nutritrack.report;

import com.nutritrack.report.ReportService.CalendarMonth;
import com.nutritrack.report.ReportService.RangeReport;
import com.nutritrack.report.ReportService.Trend;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Reports, calendar and exports")
public class ReportController {
    private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");

    private final ReportService reports;
    private final ExportService exports;

    public ReportController(ReportService reports, ExportService exports) {
        this.reports = reports;
        this.exports = exports;
    }

    @GetMapping({"/api/reports/weekly", "/api/nutrition/weekly"})
    @Operation(summary = "Monday-Sunday report for the week containing the date (default: this week)")
    public RangeReport weekly(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return reports.weekly(date);
    }

    @GetMapping({"/api/reports/monthly", "/api/nutrition/monthly"})
    @Operation(summary = "Report for a calendar month, e.g. month=2026-10 (default: this month)")
    public RangeReport monthly(@RequestParam(required = false) YearMonth month) {
        return reports.monthly(month);
    }

    @GetMapping({"/api/reports/custom", "/api/analytics/nutrition"})
    @Operation(summary = "Report for any range of up to 366 days")
    public RangeReport custom(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                              @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return reports.custom(from, to);
    }

    @GetMapping("/api/calendar")
    @Operation(summary = "Per-day overview of a month for the calendar/history page")
    public CalendarMonth calendar(@RequestParam(required = false) YearMonth month) {
        return reports.calendar(month);
    }

    @GetMapping("/api/analytics/trend")
    @Operation(summary = "Daily series of one nutrient (default: energy, last 30 days)")
    public Trend trend(@RequestParam(required = false) String code,
                       @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return reports.trend(code, from, to);
    }

    @GetMapping("/api/exports/daily-report.csv")
    public ResponseEntity<byte[]> dailyCsv(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return csv(exports.dailyReport(date), "nutritrack-daily-" + date + ".csv");
    }

    @GetMapping({"/api/exports/diary.csv", "/api/export/diary"})
    public ResponseEntity<byte[]> diaryCsv(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return csv(exports.diary(from, to), "nutritrack-diary-" + from + "-to-" + to + ".csv");
    }

    @GetMapping({"/api/exports/nutrient-summary.csv", "/api/export/nutrition"})
    public ResponseEntity<byte[]> summaryCsv(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return csv(exports.nutrientSummary(from, to), "nutritrack-nutrients-" + from + "-to-" + to + ".csv");
    }

    @GetMapping("/api/exports/weight.csv")
    public ResponseEntity<byte[]> weightCsv(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return csv(exports.weight(from, to), "nutritrack-weight-" + from + "-to-" + to + ".csv");
    }

    private static ResponseEntity<byte[]> csv(CsvWriter csv, String filename) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build()
                        .toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv.toBytes());
    }
}
