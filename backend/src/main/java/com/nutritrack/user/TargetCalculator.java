package com.nutritrack.user;

import com.nutritrack.common.ApiException;
import com.nutritrack.common.Enums.ActivityLevel;
import com.nutritrack.common.Enums.Goal;
import com.nutritrack.common.Enums.Sex;
import com.nutritrack.user.ProfileDtos.TargetSuggestion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Suggests editable starting targets. Uses the Mifflin-St Jeor equation for resting energy expenditure,
 * standard activity multipliers and a goal adjustment. These are estimates for the user to adjust, not a
 * prescribed diet.
 */
@Component
public class TargetCalculator {

    public TargetSuggestion suggest(UserProfile p) {
        List<String> missing = new ArrayList<>();
        if (p.getAge() == null) missing.add("age");
        if (p.getHeightCm() == null) missing.add("height");
        if (p.getCurrentWeightKg() == null) missing.add("current weight");
        if (!missing.isEmpty()) {
            throw ApiException.unprocessable("PROFILE_INCOMPLETE",
                    "Add your " + String.join(", ", missing) + " to get suggested targets");
        }
        List<String> notes = new ArrayList<>();
        double weight = p.getCurrentWeightKg().doubleValue();
        double height = p.getHeightCm().doubleValue();
        int age = p.getAge();
        double sexConstant;
        if (p.getSex() == Sex.MALE) {
            sexConstant = 5;
        } else if (p.getSex() == Sex.FEMALE) {
            sexConstant = -161;
        } else {
            sexConstant = -78;
            notes.add("Sex not provided: used the midpoint of the male and female equations.");
        }
        double bmr = 10 * weight + 6.25 * height - 5 * age + sexConstant;
        ActivityLevel activity = p.getActivityLevel() == null ? ActivityLevel.LIGHT : p.getActivityLevel();
        if (p.getActivityLevel() == null) {
            notes.add("Activity level not set: assumed lightly active.");
        }
        double maintenance = bmr * activityFactor(activity);
        Goal goal = p.getGoal() == null ? Goal.MAINTENANCE : p.getGoal();
        double calories = switch (goal) {
            case FAT_LOSS -> Math.max(maintenance * 0.80, bmr);
            case MUSCLE_GAIN -> maintenance * 1.10;
            case MAINTENANCE -> maintenance;
        };
        calories = Math.round(calories / 10.0) * 10.0;

        double proteinPerKg = switch (goal) {
            case FAT_LOSS -> 1.6;
            case MUSCLE_GAIN -> 1.8;
            case MAINTENANCE -> 1.2;
        };
        double protein = proteinPerKg * weight;
        double fat = calories * 0.27 / 9;
        double carbs = Math.max(0, (calories - protein * 4 - fat * 9) / 4);
        double fiber = calories / 1000 * 14;
        int water = (int) (Math.round(weight * 35 / 250.0) * 250);

        notes.add("Calories: Mifflin-St Jeor resting energy x activity factor " + activityFactor(activity)
                + (goal == Goal.FAT_LOSS ? ", minus 20% (never below resting energy)" : goal == Goal.MUSCLE_GAIN
                ? ", plus 10%" : "") + ".");
        notes.add("Protein: " + proteinPerKg + " g per kg body weight. Fat: 27% of calories. Carbohydrate: the rest.");
        notes.add("Fibre: 14 g per 1000 kcal (Institute of Medicine adequate intake basis).");
        notes.add("Water: 35 mL per kg as a starting point for plain drinking water. Needs vary a lot with climate, "
                + "activity, health and the water in your food; adjust it to suit you.");
        return new TargetSuggestion((int) calories, scale(protein), scale(carbs), scale(fat), scale(fiber), water,
                (int) Math.round(bmr), (int) Math.round(maintenance), notes);
    }

    static double activityFactor(ActivityLevel level) {
        return switch (level) {
            case SEDENTARY -> 1.2;
            case LIGHT -> 1.375;
            case MODERATE -> 1.55;
            case ACTIVE -> 1.725;
            case VERY_ACTIVE -> 1.9;
        };
    }

    private static BigDecimal scale(double v) {
        return BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_UP);
    }
}
