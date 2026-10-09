import type { ActivityLevel, DietaryPreference, Goal, Profile, ProfileUpdate } from "./types";

export const ACTIVITY_LABELS: Record<ActivityLevel, string> = {
  SEDENTARY: "Sedentary (little or no exercise)",
  LIGHT: "Lightly active (1-3 days a week)",
  MODERATE: "Moderately active (3-5 days a week)",
  ACTIVE: "Very active (6-7 days a week)",
  VERY_ACTIVE: "Extra active (physical job or twice a day)",
};

export const GOAL_LABELS: Record<Goal, string> = {
  FAT_LOSS: "Fat loss",
  MAINTENANCE: "Maintain weight",
  MUSCLE_GAIN: "Muscle gain",
};

export const DIET_LABELS: Record<DietaryPreference, string> = {
  VEGETARIAN: "Vegetarian",
  EGGETARIAN: "Eggetarian",
  NON_VEGETARIAN: "Non-vegetarian",
  VEGAN: "Vegan",
  OTHER: "Other",
};

/** The profile PUT is a full replacement, so start from the current profile and apply changes. */
export function toProfileUpdate(p: Profile, changes: Partial<ProfileUpdate> = {}): ProfileUpdate {
  const base: ProfileUpdate = {
    name: p.name,
    age: p.age,
    heightCm: p.heightCm,
    currentWeightKg: p.currentWeightKg,
    targetWeightKg: p.targetWeightKg,
    sex: p.sex,
    activityLevel: p.activityLevel,
    workoutDaysPerWeek: p.workoutDaysPerWeek,
    goal: p.goal,
    dietaryPreference: p.dietaryPreference,
    allergies: p.allergies,
    dietaryRestrictions: p.dietaryRestrictions,
    calorieTargetKcal: p.calorieTargetKcal,
    proteinTargetG: p.proteinTargetG,
    carbsTargetG: p.carbsTargetG,
    fatTargetG: p.fatTargetG,
    fiberTargetG: p.fiberTargetG,
    waterTargetMl: p.waterTargetMl,
    referenceSystem: p.referenceSystem,
    timezone: p.timezone,
    onboardingCompleted: p.onboardingCompleted,
  };
  return { ...base, ...changes };
}

/** Converts an empty form string to undefined, otherwise a number. */
export function numOrUndef(v: string | number | null | undefined): number | undefined {
  if (v === null || v === undefined || v === "") return undefined;
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
}
