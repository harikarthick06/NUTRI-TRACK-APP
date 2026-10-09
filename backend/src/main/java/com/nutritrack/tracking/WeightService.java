package com.nutritrack.tracking;

import com.nutritrack.common.ApiException;
import com.nutritrack.nutrition.NutritionSummaryService;
import com.nutritrack.security.CurrentUser;
import com.nutritrack.user.UserProfile;
import com.nutritrack.user.UserProfileRepository;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeightService {
    private static final int DEFAULT_DAYS = 90;

    private final WeightLogRepository logs;
    private final UserProfileRepository profiles;
    private final Clock clock;

    public WeightService(WeightLogRepository logs, UserProfileRepository profiles, Clock clock) {
        this.logs = logs;
        this.profiles = profiles;
        this.clock = clock;
    }

    public record WeightRequest(LocalDate date,
                                @NotNull @DecimalMin("20") @DecimalMax("400") BigDecimal weightKg,
                                @DecimalMin("30") @DecimalMax("300") BigDecimal waistCm,
                                @DecimalMin("2") @DecimalMax("75") BigDecimal bodyFatPct,
                                @Size(max = 255) String note) {
    }

    public record WeightDto(long id, LocalDate date, BigDecimal weightKg, BigDecimal waistCm, BigDecimal bodyFatPct,
                            String note) {
    }

    /** Change is latest minus the first entry in the range; the 7-day average uses entries in the last 7 days. */
    public record WeightStats(BigDecimal latestKg, LocalDate latestDate, BigDecimal startKg, BigDecimal changeKg,
                              BigDecimal lowestKg, BigDecimal highestKg, BigDecimal sevenDayAverageKg,
                              BigDecimal targetWeightKg, BigDecimal toTargetKg) {
    }

    public record WeightHistory(LocalDate from, LocalDate to, List<WeightDto> entries, WeightStats stats) {
    }

    @Transactional(readOnly = true)
    public WeightHistory history(LocalDate from, LocalDate to) {
        long uid = CurrentUser.id();
        LocalDate end = to != null ? to : today(uid);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("INVALID_RANGE", "'from' must be on or before 'to'");
        }
        if (start.plusYears(5).isBefore(end)) {
            throw ApiException.badRequest("RANGE_TOO_LONG", "Choose a range of five years or less");
        }
        List<WeightLog> list = logs.findByUserIdAndLogDateBetweenOrderByLogDateAsc(uid, start, end);
        BigDecimal target = profiles.findById(uid).map(UserProfile::getTargetWeightKg).orElse(null);
        return new WeightHistory(start, end, list.stream().map(WeightService::dto).toList(), stats(list, end, target));
    }

    @Transactional
    public WeightDto create(WeightRequest request) {
        long uid = CurrentUser.id();
        LocalDate date = request.date() != null ? request.date() : today(uid);
        checkNotFuture(uid, date);
        if (logs.findByUserIdAndLogDate(uid, date).isPresent()) {
            throw ApiException.conflict("WEIGHT_EXISTS", "There is already a weight for " + date + "; edit that entry");
        }
        WeightLog log = new WeightLog(uid);
        apply(log, request, date);
        logs.saveAndFlush(log);
        syncProfile(uid);
        return dto(log);
    }

    @Transactional
    public WeightDto update(long id, WeightRequest request) {
        long uid = CurrentUser.id();
        WeightLog log = logs.findByIdAndUserId(id, uid).orElseThrow(() -> ApiException.notFound("Weight entry"));
        LocalDate date = request.date() != null ? request.date() : log.getLogDate();
        checkNotFuture(uid, date);
        logs.findByUserIdAndLogDate(uid, date).filter(other -> !other.getId().equals(log.getId())).ifPresent(other -> {
            throw ApiException.conflict("WEIGHT_EXISTS", "There is already a weight for " + date);
        });
        apply(log, request, date);
        logs.saveAndFlush(log);
        syncProfile(uid);
        return dto(log);
    }

    @Transactional
    public void delete(long id) {
        long uid = CurrentUser.id();
        WeightLog log = logs.findByIdAndUserId(id, uid).orElseThrow(() -> ApiException.notFound("Weight entry"));
        logs.delete(log);
        logs.flush();
        syncProfile(uid);
    }

    static WeightStats stats(List<WeightLog> list, LocalDate end, BigDecimal target) {
        if (list.isEmpty()) {
            return new WeightStats(null, null, null, null, null, null, null, target, null);
        }
        WeightLog first = list.get(0);
        WeightLog last = list.get(list.size() - 1);
        BigDecimal lowest = list.stream().map(WeightLog::getWeightKg).min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal highest = list.stream().map(WeightLog::getWeightKg).max(Comparator.naturalOrder()).orElseThrow();
        List<BigDecimal> recent = list.stream().filter(l -> !l.getLogDate().isBefore(end.minusDays(6)))
                .map(WeightLog::getWeightKg).toList();
        BigDecimal avg = recent.isEmpty() ? null : recent.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recent.size()), MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP);
        return new WeightStats(last.getWeightKg(), last.getLogDate(), first.getWeightKg(),
                last.getWeightKg().subtract(first.getWeightKg()), lowest, highest, avg, target,
                target == null ? null : target.subtract(last.getWeightKg()));
    }

    /** Keeps the profile's current weight equal to the most recent log. */
    private void syncProfile(long uid) {
        profiles.findById(uid).ifPresent(p -> logs.findFirstByUserIdOrderByLogDateDesc(uid).ifPresent(latest -> {
            p.setCurrentWeightKg(latest.getWeightKg());
            profiles.save(p);
        }));
    }

    private void checkNotFuture(long uid, LocalDate date) {
        if (date.isAfter(today(uid))) {
            throw ApiException.badRequest("FUTURE_DATE", "You can't log a weight for a future date");
        }
    }

    private static void apply(WeightLog log, WeightRequest r, LocalDate date) {
        log.setLogDate(date);
        log.setWeightKg(r.weightKg().setScale(2, RoundingMode.HALF_UP));
        log.setWaistCm(r.waistCm());
        log.setBodyFatPct(r.bodyFatPct());
        log.setNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());
    }

    static WeightDto dto(WeightLog l) {
        return new WeightDto(l.getId(), l.getLogDate(), l.getWeightKg(), l.getWaistCm(), l.getBodyFatPct(),
                l.getNote());
    }

    private LocalDate today(long uid) {
        return LocalDate.ofInstant(Instant.now(clock), NutritionSummaryService.safeZone(
                profiles.findById(uid).map(UserProfile::getTimezone).orElse(null)));
    }
}
