package com.nutritrack.user;

import com.nutritrack.common.ApiException;
import com.nutritrack.security.CurrentUser;
import com.nutritrack.user.ProfileDtos.ProfileResponse;
import com.nutritrack.user.ProfileDtos.TargetSuggestion;
import com.nutritrack.user.ProfileDtos.UpdateProfileRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserProfileRepository profiles;
    private final UserRepository users;
    private final TargetCalculator targetCalculator;

    public ProfileService(UserProfileRepository profiles, UserRepository users, TargetCalculator targetCalculator) {
        this.profiles = profiles;
        this.users = users;
        this.targetCalculator = targetCalculator;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get() {
        return toResponse(load());
    }

    @Transactional
    public ProfileResponse update(UpdateProfileRequest r) {
        UserProfile p = load();
        p.setName(r.name().trim());
        p.setAge(r.age());
        p.setHeightCm(r.heightCm());
        p.setCurrentWeightKg(r.currentWeightKg());
        p.setTargetWeightKg(r.targetWeightKg());
        p.setSex(r.sex());
        p.setActivityLevel(r.activityLevel());
        p.setWorkoutDaysPerWeek(r.workoutDaysPerWeek());
        p.setGoal(r.goal());
        p.setDietaryPreference(r.dietaryPreference());
        p.setAllergies(blankToNull(r.allergies()));
        p.setDietaryRestrictions(blankToNull(r.dietaryRestrictions()));
        p.setCalorieTargetKcal(r.calorieTargetKcal());
        p.setProteinTargetG(r.proteinTargetG());
        p.setCarbsTargetG(r.carbsTargetG());
        p.setFatTargetG(r.fatTargetG());
        p.setFiberTargetG(r.fiberTargetG());
        p.setWaterTargetMl(r.waterTargetMl());
        if (r.referenceSystem() != null) {
            p.setReferenceSystem(r.referenceSystem());
        }
        if (r.timezone() != null) {
            try {
                p.setTimezone(ZoneId.of(r.timezone()).getId());
            } catch (DateTimeException e) {
                throw ApiException.badRequest("INVALID_TIMEZONE", "Unknown time zone");
            }
        }
        if (r.onboardingCompleted() != null) {
            p.setOnboardingCompleted(r.onboardingCompleted());
        }
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public TargetSuggestion suggestTargets() {
        return targetCalculator.suggest(load());
    }

    UserProfile load() {
        return profiles.findById(CurrentUser.id()).orElseThrow(() -> ApiException.notFound("Profile"));
    }

    private ProfileResponse toResponse(UserProfile p) {
        String email = users.findById(p.getUserId()).map(User::getEmail).orElse(null);
        BigDecimal bmi = null;
        if (p.getHeightCm() != null && p.getCurrentWeightKg() != null && p.getHeightCm().signum() > 0) {
            BigDecimal m = p.getHeightCm().divide(BigDecimal.valueOf(100));
            bmi = p.getCurrentWeightKg().divide(m.multiply(m), 1, RoundingMode.HALF_UP);
        }
        return new ProfileResponse(p.getUserId(), email, p.getName(), p.getAge(), p.getHeightCm(),
                p.getCurrentWeightKg(), p.getTargetWeightKg(), p.getSex(), p.getActivityLevel(),
                p.getWorkoutDaysPerWeek(), p.getGoal(), p.getWeightUnit(), p.getHeightUnit(), p.getEnergyUnit(),
                p.getDietaryPreference(), p.getAllergies(), p.getDietaryRestrictions(), p.getCalorieTargetKcal(),
                p.getProteinTargetG(), p.getCarbsTargetG(), p.getFatTargetG(), p.getFiberTargetG(),
                p.getWaterTargetMl(), p.getReferenceSystem(), p.getTimezone(), p.isOnboardingCompleted(), bmi);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
