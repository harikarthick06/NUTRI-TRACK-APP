package com.nutritrack.food.provider;

import com.nutritrack.common.Enums.PreparationState;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reads the preparation state from a provider description such as "Rice, white, cooked". */
final class PreparationStateGuesser {
    private static final Pattern COOKED = Pattern.compile(
            "\\b(cooked|boiled|roasted|baked|fried|grilled|steamed|braised|stewed|broiled|microwaved|poached|scrambled)\\b");
    private static final Pattern RAW = Pattern.compile("\\braw\\b");
    private static final Pattern DRY = Pattern.compile("\\b(dry|dried|uncooked)\\b");

    private PreparationStateGuesser() {
    }

    static PreparationState guess(String description) {
        if (description == null) {
            return PreparationState.NOT_APPLICABLE;
        }
        String d = description.toLowerCase(Locale.ROOT);
        if (COOKED.matcher(d).find()) {
            return PreparationState.COOKED;
        }
        if (RAW.matcher(d).find()) {
            return PreparationState.RAW;
        }
        if (DRY.matcher(d).find()) {
            return PreparationState.DRY;
        }
        return PreparationState.NOT_APPLICABLE;
    }
}
