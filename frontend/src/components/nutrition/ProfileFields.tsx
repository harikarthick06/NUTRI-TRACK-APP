import type { UseFormRegister, FieldErrors } from "react-hook-form";
import { z } from "zod";
import { Field } from "@/components/ui/label";
import { Input, NativeSelect, Textarea } from "@/components/ui/input";
import { ACTIVITY_LABELS, DIET_LABELS, GOAL_LABELS } from "@/lib/profile";

const optionalNumber = (min: number, max: number, label: string) =>
  z
    .string()
    .trim()
    .refine((v) => v === "" || (!Number.isNaN(Number(v)) && Number(v) >= min && Number(v) <= max), {
      message: `${label} must be between ${min} and ${max}`,
    });

export const bodySchema = z.object({
  name: z.string().trim().min(1, "Enter your name").max(100),
  age: optionalNumber(14, 120, "Age").refine((v) => v === "" || Number.isInteger(Number(v)), "Use whole years"),
  sex: z.enum(["", "FEMALE", "MALE"]),
  heightCm: optionalNumber(50, 272, "Height"),
  currentWeightKg: optionalNumber(20, 400, "Weight"),
  targetWeightKg: optionalNumber(20, 400, "Target weight"),
  activityLevel: z.enum(["", "SEDENTARY", "LIGHT", "MODERATE", "ACTIVE", "VERY_ACTIVE"]),
  workoutDaysPerWeek: optionalNumber(0, 14, "Workout days"),
  goal: z.enum(["", "FAT_LOSS", "MAINTENANCE", "MUSCLE_GAIN"]),
  dietaryPreference: z.enum(["", "VEGETARIAN", "EGGETARIAN", "NON_VEGETARIAN", "VEGAN", "OTHER"]),
  allergies: z.string().max(1000),
  dietaryRestrictions: z.string().max(1000),
});
export type BodyValues = z.infer<typeof bodySchema>;

export const targetsSchema = z.object({
  calorieTargetKcal: optionalNumber(800, 10000, "Calories"),
  proteinTargetG: optionalNumber(0, 600, "Protein"),
  carbsTargetG: optionalNumber(0, 1500, "Carbohydrate"),
  fatTargetG: optionalNumber(0, 600, "Fat"),
  fiberTargetG: optionalNumber(0, 200, "Fibre"),
  waterTargetMl: optionalNumber(0, 10000, "Water"),
});
export type TargetValues = z.infer<typeof targetsSchema>;

export function BodyFields({
  register,
  errors,
  section,
}: {
  register: UseFormRegister<BodyValues>;
  errors: FieldErrors<BodyValues>;
  section: "body" | "lifestyle" | "all";
}) {
  const body = section !== "lifestyle";
  const lifestyle = section !== "body";
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {body && (
        <>
          <Field label="Name" htmlFor="name" error={errors.name?.message} className="sm:col-span-2">
            <Input id="name" {...register("name")} aria-invalid={!!errors.name} />
          </Field>
          <Field label="Age" htmlFor="age" error={errors.age?.message}>
            <Input id="age" type="number" inputMode="numeric" {...register("age")} aria-invalid={!!errors.age} />
          </Field>
          <Field label="Sex (optional)" htmlFor="sex" hint="Only used to pick reference values and estimate energy needs">
            <NativeSelect id="sex" {...register("sex")}>
              <option value="">Prefer not to say</option>
              <option value="FEMALE">Female</option>
              <option value="MALE">Male</option>
            </NativeSelect>
          </Field>
          <Field label="Height (cm)" htmlFor="heightCm" error={errors.heightCm?.message}>
            <Input id="heightCm" type="number" inputMode="decimal" step="0.1" {...register("heightCm")} aria-invalid={!!errors.heightCm} />
          </Field>
          <Field label="Current weight (kg)" htmlFor="currentWeightKg" error={errors.currentWeightKg?.message}>
            <Input id="currentWeightKg" type="number" inputMode="decimal" step="0.1" {...register("currentWeightKg")} aria-invalid={!!errors.currentWeightKg} />
          </Field>
          <Field label="Target weight (kg)" htmlFor="targetWeightKg" error={errors.targetWeightKg?.message}>
            <Input id="targetWeightKg" type="number" inputMode="decimal" step="0.1" {...register("targetWeightKg")} aria-invalid={!!errors.targetWeightKg} />
          </Field>
        </>
      )}
      {lifestyle && (
        <>
          <Field label="Activity level" htmlFor="activityLevel">
            <NativeSelect id="activityLevel" {...register("activityLevel")}>
              <option value="">Not set</option>
              {Object.entries(ACTIVITY_LABELS).map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label="Workouts per week" htmlFor="workoutDaysPerWeek" error={errors.workoutDaysPerWeek?.message}>
            <Input id="workoutDaysPerWeek" type="number" inputMode="numeric" {...register("workoutDaysPerWeek")} />
          </Field>
          <Field label="Main goal" htmlFor="goal">
            <NativeSelect id="goal" {...register("goal")}>
              <option value="">Not set</option>
              {Object.entries(GOAL_LABELS).map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label="Dietary preference" htmlFor="dietaryPreference">
            <NativeSelect id="dietaryPreference" {...register("dietaryPreference")}>
              <option value="">Not set</option>
              {Object.entries(DIET_LABELS).map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label="Allergies" htmlFor="allergies" className="sm:col-span-2">
            <Textarea id="allergies" rows={2} placeholder="e.g. peanuts, shellfish" {...register("allergies")} />
          </Field>
          <Field label="Other dietary restrictions" htmlFor="dietaryRestrictions" className="sm:col-span-2">
            <Textarea id="dietaryRestrictions" rows={2} placeholder="e.g. no onion or garlic" {...register("dietaryRestrictions")} />
          </Field>
        </>
      )}
    </div>
  );
}

export function TargetFields({ register, errors }: { register: UseFormRegister<TargetValues>; errors: FieldErrors<TargetValues> }) {
  const fields: { name: keyof TargetValues; label: string; step: string }[] = [
    { name: "calorieTargetKcal", label: "Calories (kcal)", step: "1" },
    { name: "proteinTargetG", label: "Protein (g)", step: "0.1" },
    { name: "carbsTargetG", label: "Carbohydrate (g)", step: "0.1" },
    { name: "fatTargetG", label: "Fat (g)", step: "0.1" },
    { name: "fiberTargetG", label: "Fibre (g)", step: "0.1" },
    { name: "waterTargetMl", label: "Water goal (mL)", step: "50" },
  ];
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {fields.map((f) => (
        <Field key={f.name} label={f.label} htmlFor={f.name} error={errors[f.name]?.message}>
          <Input id={f.name} type="number" inputMode="decimal" step={f.step} {...register(f.name)} aria-invalid={!!errors[f.name]} />
        </Field>
      ))}
    </div>
  );
}

const s = (v: number | undefined | null) => (v === undefined || v === null ? "" : String(v));
export { s as numToField };
