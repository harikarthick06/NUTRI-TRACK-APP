package com.nutritrack.tracking;

import com.nutritrack.common.ApiException;
import com.nutritrack.nutrition.NutritionSummaryService;
import com.nutritrack.security.CurrentUser;
import com.nutritrack.user.UserProfile;
import com.nutritrack.user.UserProfileRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaterService {
    public static final String NEEDS_VARY = "Water needs vary with body size, activity, climate, pregnancy and "
            + "health conditions, and food and other drinks provide water too. Set a goal that suits you, or ask a "
            + "doctor if you have a condition that affects fluids.";

    private final WaterLogRepository logs;
    private final UserProfileRepository profiles;
    private final Clock clock;

    public WaterService(WaterLogRepository logs, UserProfileRepository profiles, Clock clock) {
        this.logs = logs;
        this.profiles = profiles;
        this.clock = clock;
    }

    public record WaterRequest(LocalDate date, @NotNull @Min(1) @Max(5000) Integer amountMl, Instant loggedAt,
                               @Size(max = 255) String note) {
    }

    public record WaterDto(long id, LocalDate date, Instant loggedAt, int amountMl, String note) {
    }

    public record WaterDay(LocalDate date, int totalMl, Integer targetMl, Integer percentOfTarget, String note,
                           List<WaterDto> entries) {
    }

    public record WaterHistoryDay(LocalDate date, int totalMl) {
    }

    public record WaterHistory(LocalDate from, LocalDate to, Integer targetMl, List<WaterHistoryDay> days) {
    }

    @Transactional(readOnly = true)
    public WaterDay day(LocalDate date) {
        long uid = CurrentUser.id();
        LocalDate d = date != null ? date : today(uid);
        List<WaterDto> entries = logs.findByUserIdAndLogDateOrderByLoggedAtAsc(uid, d).stream()
                .map(WaterService::dto).toList();
        int total = entries.stream().mapToInt(WaterDto::amountMl).sum();
        Integer target = profiles.findById(uid).map(UserProfile::getWaterTargetMl).orElse(null);
        Integer pct = target == null || target == 0 ? null : (int) Math.round(total * 100.0 / target);
        return new WaterDay(d, total, target, pct, NEEDS_VARY, entries);
    }

    @Transactional(readOnly = true)
    public WaterHistory history(LocalDate from, LocalDate to) {
        long uid = CurrentUser.id();
        LocalDate end = to != null ? to : today(uid);
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end) || start.plusDays(366).isBefore(end)) {
            throw ApiException.badRequest("INVALID_RANGE", "Choose a range of up to one year");
        }
        Map<LocalDate, Integer> totals = new HashMap<>();
        logs.dailyTotals(uid, start, end).forEach(t -> totals.put(t.getLogDate(), t.getTotalMl().intValue()));
        List<WaterHistoryDay> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            days.add(new WaterHistoryDay(d, totals.getOrDefault(d, 0)));
        }
        return new WaterHistory(start, end, profiles.findById(uid).map(UserProfile::getWaterTargetMl).orElse(null),
                days);
    }

    @Transactional
    public WaterDay add(WaterRequest request) {
        long uid = CurrentUser.id();
        WaterLog log = new WaterLog(uid);
        apply(uid, log, request, null);
        logs.save(log);
        return day(log.getLogDate());
    }

    @Transactional
    public WaterDay update(long id, WaterRequest request) {
        long uid = CurrentUser.id();
        WaterLog log = logs.findByIdAndUserId(id, uid).orElseThrow(() -> ApiException.notFound("Water entry"));
        apply(uid, log, request, log.getLogDate());
        logs.save(log);
        return day(log.getLogDate());
    }

    @Transactional
    public WaterDay delete(long id) {
        long uid = CurrentUser.id();
        WaterLog log = logs.findByIdAndUserId(id, uid).orElseThrow(() -> ApiException.notFound("Water entry"));
        LocalDate d = log.getLogDate();
        logs.delete(log);
        logs.flush();
        return day(d);
    }

    private void apply(long uid, WaterLog log, WaterRequest r, LocalDate existingDate) {
        ZoneId zone = zone(uid);
        LocalDate today = LocalDate.ofInstant(Instant.now(clock), zone);
        LocalDate date = r.date() != null ? r.date()
                : existingDate != null ? existingDate
                : r.loggedAt() != null ? LocalDate.ofInstant(r.loggedAt(), zone) : today;
        if (date.isAfter(today)) {
            throw ApiException.badRequest("FUTURE_DATE", "You can't log water for a future date");
        }
        log.setLogDate(date);
        log.setAmountMl(r.amountMl());
        log.setLoggedAt(r.loggedAt() != null ? r.loggedAt()
                : log.getLoggedAt() != null ? log.getLoggedAt()
                : date.equals(today) ? Instant.now(clock) : date.atTime(12, 0).atZone(zone).toInstant());
        log.setNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());
    }

    private static WaterDto dto(WaterLog l) {
        return new WaterDto(l.getId(), l.getLogDate(), l.getLoggedAt(), l.getAmountMl(), l.getNote());
    }

    private ZoneId zone(long uid) {
        return NutritionSummaryService.safeZone(profiles.findById(uid).map(UserProfile::getTimezone).orElse(null));
    }

    private LocalDate today(long uid) {
        return LocalDate.ofInstant(Instant.now(clock), zone(uid));
    }
}
