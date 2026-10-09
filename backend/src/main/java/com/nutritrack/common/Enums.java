package com.nutritrack.common;

/** Shared enumerations persisted as strings. */
public final class Enums {
    private Enums() {
    }

    public enum Sex { FEMALE, MALE }

    public enum ActivityLevel { SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE }

    public enum Goal { FAT_LOSS, MAINTENANCE, MUSCLE_GAIN }

    public enum DietaryPreference { VEGETARIAN, EGGETARIAN, NON_VEGETARIAN, VEGAN, OTHER }

    public enum MealType { BREAKFAST, MORNING_SNACK, LUNCH, EVENING_SNACK, DINNER, OTHER }

    public enum FoodType { GENERIC, BRANDED, RECIPE, CUSTOM }

    public enum PreparationState { RAW, COOKED, DRY, NOT_APPLICABLE }

    public enum DataQuality { VERIFIED, EXTERNAL, USER_ENTERED, ESTIMATED }

    public enum BasisUnit { G, ML }

    /**
     * How an entered quantity relates to the food's own state. COOKED_WEIGHT_OF_RAW_FOOD means the user
     * weighed the food after cooking but picked a raw food; it is converted with the food's cooked yield
     * factor and rejected when no factor exists.
     */
    public enum WeightState { AS_DEFINED, COOKED_WEIGHT_OF_RAW_FOOD }
}
