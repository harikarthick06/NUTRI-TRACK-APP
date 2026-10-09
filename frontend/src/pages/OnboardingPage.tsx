import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { Check, Sparkles } from "lucide-react";
import { Logo } from "@/components/layout/AppLayout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { PageSkeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/ui/empty-state";
import {
  BodyFields,
  TargetFields,
  bodySchema,
  numToField,
  targetsSchema,
  type BodyValues,
  type TargetValues,
} from "@/components/nutrition/ProfileFields";
import { useProfile, useTargetSuggestions, useUpdateProfile } from "@/hooks/queries";
import { useAuth } from "@/lib/auth";
import { errorMessage } from "@/lib/api";
import { numOrUndef, toProfileUpdate } from "@/lib/profile";
import type { ActivityLevel, DietaryPreference, Goal, Profile, Sex } from "@/lib/types";
import { cn, fmt } from "@/lib/utils";

const STEPS = ["About you", "Lifestyle", "Daily targets"];

export function bodyDefaults(p: Profile): BodyValues {
  return {
    name: p.name,
    age: numToField(p.age),
    sex: p.sex ?? "",
    heightCm: numToField(p.heightCm),
    currentWeightKg: numToField(p.currentWeightKg),
    targetWeightKg: numToField(p.targetWeightKg),
    activityLevel: p.activityLevel ?? "",
    workoutDaysPerWeek: numToField(p.workoutDaysPerWeek),
    goal: p.goal ?? "",
    dietaryPreference: p.dietaryPreference ?? "",
    allergies: p.allergies ?? "",
    dietaryRestrictions: p.dietaryRestrictions ?? "",
  };
}

export function bodyToUpdate(v: BodyValues) {
  return {
    name: v.name.trim(),
    age: numOrUndef(v.age),
    sex: (v.sex || undefined) as Sex | undefined,
    heightCm: numOrUndef(v.heightCm),
    currentWeightKg: numOrUndef(v.currentWeightKg),
    targetWeightKg: numOrUndef(v.targetWeightKg),
    activityLevel: (v.activityLevel || undefined) as ActivityLevel | undefined,
    workoutDaysPerWeek: numOrUndef(v.workoutDaysPerWeek),
    goal: (v.goal || undefined) as Goal | undefined,
    dietaryPreference: (v.dietaryPreference || undefined) as DietaryPreference | undefined,
    allergies: v.allergies.trim() || undefined,
    dietaryRestrictions: v.dietaryRestrictions.trim() || undefined,
  };
}

export function targetDefaults(p: Profile): TargetValues {
  return {
    calorieTargetKcal: numToField(p.calorieTargetKcal),
    proteinTargetG: numToField(p.proteinTargetG),
    carbsTargetG: numToField(p.carbsTargetG),
    fatTargetG: numToField(p.fatTargetG),
    fiberTargetG: numToField(p.fiberTargetG),
    waterTargetMl: numToField(p.waterTargetMl),
  };
}

export function targetsToUpdate(v: TargetValues) {
  return {
    calorieTargetKcal: numOrUndef(v.calorieTargetKcal),
    proteinTargetG: numOrUndef(v.proteinTargetG),
    carbsTargetG: numOrUndef(v.carbsTargetG),
    fatTargetG: numOrUndef(v.fatTargetG),
    fiberTargetG: numOrUndef(v.fiberTargetG),
    waterTargetMl: numOrUndef(v.waterTargetMl),
  };
}

export default function OnboardingPage() {
  const profile = useProfile();
  if (profile.isLoading) return <div className="p-6"><PageSkeleton /></div>;
  if (profile.error || !profile.data) return <div className="p-6"><ErrorState message={errorMessage(profile.error)} onRetry={() => profile.refetch()} /></div>;
  return <Onboarding profile={profile.data} />;
}

function Onboarding({ profile }: { profile: Profile }) {
  const [step, setStep] = useState(0);
  const navigate = useNavigate();
  const { user, setUser } = useAuth();
  const update = useUpdateProfile();
  const bodyForm = useForm<BodyValues>({ resolver: zodResolver(bodySchema), defaultValues: bodyDefaults(profile) });
  const targetForm = useForm<TargetValues>({ resolver: zodResolver(targetsSchema), defaultValues: targetDefaults(profile) });
  const suggestions = useTargetSuggestions(step === 2);

  useEffect(() => {
    // Fill empty target fields with the suggestion once it arrives.
    const s = suggestions.data;
    if (!s) return;
    const current = targetForm.getValues();
    const fill = (k: keyof TargetValues, v: number) => {
      if (current[k] === "") targetForm.setValue(k, String(v));
    };
    fill("calorieTargetKcal", s.calorieTargetKcal);
    fill("proteinTargetG", s.proteinTargetG);
    fill("carbsTargetG", s.carbsTargetG);
    fill("fatTargetG", s.fatTargetG);
    fill("fiberTargetG", s.fiberTargetG);
    fill("waterTargetMl", s.waterTargetMl);
  }, [suggestions.data, targetForm]);

  const saveBody = bodyForm.handleSubmit(async (v) => {
    try {
      await update.mutateAsync(toProfileUpdate(profile, bodyToUpdate(v)));
      setStep((s) => s + 1);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  });

  const finish = targetForm.handleSubmit(async (v) => {
    try {
      const current = update.data ?? profile;
      await update.mutateAsync(toProfileUpdate(current, { ...targetsToUpdate(v), onboardingCompleted: true }));
      if (user) setUser({ ...user, onboardingCompleted: true });
      toast.success("You're all set");
      navigate("/dashboard", { replace: true });
    } catch (err) {
      toast.error(errorMessage(err));
    }
  });

  return (
    <div className="min-h-dvh bg-background px-4 py-8">
      <div className="mx-auto max-w-2xl">
        <div className="mb-6 flex justify-center">
          <Logo />
        </div>
        <ol className="mb-6 flex items-center justify-center gap-2 text-sm" aria-label="Progress">
          {STEPS.map((label, i) => (
            <li key={label} className="flex items-center gap-2" aria-current={i === step ? "step" : undefined}>
              <span
                className={cn(
                  "grid size-7 place-items-center rounded-full border text-xs font-medium",
                  i < step && "border-primary bg-primary text-primary-foreground",
                  i === step && "border-primary text-primary",
                )}
              >
                {i < step ? <Check className="size-4" /> : i + 1}
              </span>
              <span className={cn("hidden sm:inline", i !== step && "text-muted-foreground")}>{label}</span>
              {i < STEPS.length - 1 && <span className="h-px w-6 bg-border" aria-hidden />}
            </li>
          ))}
        </ol>
        <Card>
          <CardHeader>
            <CardTitle className="text-xl">{STEPS[step]}</CardTitle>
            <CardDescription>
              {step === 0 && "These help estimate your energy needs. Everything except your name is optional and you can change it later."}
              {step === 1 && "How active you are and what you're working towards."}
              {step === 2 && "Suggested from your profile. They're starting points, not a diet plan, so change anything you like."}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {step < 2 ? (
              <form onSubmit={saveBody} className="grid gap-6" noValidate>
                <BodyFields register={bodyForm.register} errors={bodyForm.formState.errors} section={step === 0 ? "body" : "lifestyle"} />
                <div className="flex justify-between">
                  <Button variant="outline" disabled={step === 0} onClick={() => setStep((s) => s - 1)}>
                    Back
                  </Button>
                  <Button type="submit" disabled={update.isPending}>
                    {update.isPending ? "Saving…" : "Continue"}
                  </Button>
                </div>
              </form>
            ) : (
              <form onSubmit={finish} className="grid gap-6" noValidate>
                {suggestions.data && (
                  <div className="rounded-lg bg-primary/5 p-4 text-sm">
                    <p className="mb-1 flex items-center gap-2 font-medium">
                      <Sparkles className="size-4 text-primary" aria-hidden />
                      Estimated maintenance: {fmt(suggestions.data.maintenanceKcal)} kcal/day
                    </p>
                    <ul className="list-disc space-y-1 pl-5 text-muted-foreground">
                      {suggestions.data.notes.map((n) => (
                        <li key={n}>{n}</li>
                      ))}
                    </ul>
                  </div>
                )}
                {suggestions.error && (
                  <p className="rounded-lg bg-warning/15 p-3 text-sm">{errorMessage(suggestions.error)}. You can still enter targets yourself.</p>
                )}
                <TargetFields register={targetForm.register} errors={targetForm.formState.errors} />
                <div className="flex justify-between">
                  <Button variant="outline" onClick={() => setStep(1)}>
                    Back
                  </Button>
                  <Button type="submit" disabled={update.isPending}>
                    {update.isPending ? "Saving…" : "Finish"}
                  </Button>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
