import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { Monitor, Moon, Sparkles, Sun } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErrorState } from "@/components/ui/empty-state";
import { Input, NativeSelect } from "@/components/ui/input";
import { Field } from "@/components/ui/label";
import { PageSkeleton, Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { BodyFields, TargetFields, bodySchema, targetsSchema, type BodyValues, type TargetValues } from "@/components/nutrition/ProfileFields";
import { typeLabel } from "@/components/nutrition/NutrientTable";
import {
  useProfile,
  useReferenceValues,
  useResetTarget,
  useTargetSuggestions,
  useTargets,
  useUpdateProfile,
  useUpdateTargets,
} from "@/hooks/queries";
import { api, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { toProfileUpdate } from "@/lib/profile";
import { useTheme, type Theme } from "@/lib/theme";
import type { AuthResponse, NutrientTarget, Profile } from "@/lib/types";
import { amountWithUnit, cn, fmt, unitLabel } from "@/lib/utils";
import { bodyDefaults, bodyToUpdate, targetDefaults, targetsToUpdate } from "./OnboardingPage";
import { passwordRule } from "./RegisterPage";

function ProfileTab({ profile }: { profile: Profile }) {
  const update = useUpdateProfile();
  const { user, setUser } = useAuth();
  const form = useForm<BodyValues>({ resolver: zodResolver(bodySchema), defaultValues: bodyDefaults(profile) });
  const submit = form.handleSubmit(async (v) => {
    try {
      const p = await update.mutateAsync(toProfileUpdate(profile, bodyToUpdate(v)));
      if (user) setUser({ ...user, name: p.name });
      form.reset(bodyDefaults(p));
      toast.success("Profile saved");
    } catch (err) {
      toast.error(errorMessage(err));
    }
  });
  return (
    <Card>
      <CardHeader>
        <CardTitle>Profile</CardTitle>
        <CardDescription>
          {profile.email}
          {profile.bmi !== undefined && ` · BMI ${fmt(profile.bmi, 1)} (a rough screening number; it doesn't account for muscle or frame)`}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="grid gap-6" noValidate>
          <BodyFields register={form.register} errors={form.formState.errors} section="all" />
          <Field label="Time zone" htmlFor="tz" hint="Decides which day 'today' is">
            <TimezoneSelect profile={profile} />
          </Field>
          <div className="flex justify-end">
            <Button type="submit" disabled={update.isPending || !form.formState.isDirty}>
              {update.isPending ? "Saving…" : "Save profile"}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

function TimezoneSelect({ profile }: { profile: Profile }) {
  const update = useUpdateProfile();
  const zones = useMemo(() => {
    const supported = (Intl as unknown as { supportedValuesOf?: (k: string) => string[] }).supportedValuesOf?.("timeZone");
    const list = supported?.length ? supported : ["Asia/Kolkata", "UTC"];
    return list.includes(profile.timezone) ? list : [profile.timezone, ...list];
  }, [profile.timezone]);
  return (
    <NativeSelect
      id="tz"
      value={profile.timezone}
      onChange={async (e) => {
        try {
          await update.mutateAsync(toProfileUpdate(profile, { timezone: e.target.value }));
          toast.success("Time zone updated");
        } catch (err) {
          toast.error(errorMessage(err));
        }
      }}
    >
      {zones.map((z) => (
        <option key={z} value={z}>
          {z}
        </option>
      ))}
    </NativeSelect>
  );
}

function MacroTargetsCard({ profile }: { profile: Profile }) {
  const update = useUpdateProfile();
  const form = useForm<TargetValues>({ resolver: zodResolver(targetsSchema), defaultValues: targetDefaults(profile) });
  const [wantSuggestion, setWantSuggestion] = useState(false);
  const suggestion = useTargetSuggestions(wantSuggestion);
  const submit = form.handleSubmit(async (v) => {
    try {
      const p = await update.mutateAsync(toProfileUpdate(profile, targetsToUpdate(v)));
      form.reset(targetDefaults(p));
      toast.success("Targets saved");
    } catch (err) {
      toast.error(errorMessage(err));
    }
  });
  const apply = () => {
    const s = suggestion.data;
    if (!s) return;
    const opts = { shouldDirty: true };
    form.setValue("calorieTargetKcal", String(s.calorieTargetKcal), opts);
    form.setValue("proteinTargetG", String(s.proteinTargetG), opts);
    form.setValue("carbsTargetG", String(s.carbsTargetG), opts);
    form.setValue("fatTargetG", String(s.fatTargetG), opts);
    form.setValue("fiberTargetG", String(s.fiberTargetG), opts);
    form.setValue("waterTargetMl", String(s.waterTargetMl), opts);
  };
  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-start justify-between gap-2">
        <div>
          <CardTitle>Calories, macros and water</CardTitle>
          <CardDescription>Your own targets. Leave a field empty for no target.</CardDescription>
        </div>
        <Button variant="outline" size="sm" onClick={() => setWantSuggestion(true)}>
          <Sparkles /> Suggest from my profile
        </Button>
      </CardHeader>
      <CardContent className="grid gap-4">
        {wantSuggestion && suggestion.isLoading && <Skeleton className="h-16" />}
        {suggestion.error && <p className="rounded-md bg-warning/15 p-3 text-sm">{errorMessage(suggestion.error)}</p>}
        {suggestion.data && (
          <div className="rounded-lg bg-primary/5 p-3 text-sm">
            <p className="font-medium">
              Suggested: {fmt(suggestion.data.calorieTargetKcal)} kcal · P {fmt(suggestion.data.proteinTargetG)} g · C {fmt(suggestion.data.carbsTargetG)} g · F{" "}
              {fmt(suggestion.data.fatTargetG)} g · fibre {fmt(suggestion.data.fiberTargetG)} g · water {fmt(suggestion.data.waterTargetMl)} mL
            </p>
            <ul className="mt-1 list-disc pl-5 text-xs text-muted-foreground">
              {suggestion.data.notes.map((n) => (
                <li key={n}>{n}</li>
              ))}
            </ul>
            <Button size="sm" className="mt-2" onClick={apply}>
              Use these values
            </Button>
          </div>
        )}
        <form onSubmit={submit} className="grid gap-4" noValidate>
          <TargetFields register={form.register} errors={form.formState.errors} />
          <div className="flex justify-end">
            <Button type="submit" disabled={update.isPending || !form.formState.isDirty}>
              Save targets
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

function EditTargetDialog({ nutrient, onClose }: { nutrient: NutrientTarget | null; onClose: () => void }) {
  const update = useUpdateTargets();
  const [target, setTarget] = useState("");
  const [upper, setUpper] = useState("");
  const [lastCode, setLastCode] = useState<string | null>(null);
  if (nutrient && nutrient.code !== lastCode) {
    setLastCode(nutrient.code);
    setTarget(nutrient.targetType === "CUSTOM" && nutrient.target !== undefined ? String(nutrient.target) : "");
    setUpper(nutrient.upperLimitType === "CUSTOM" && nutrient.upperLimit !== undefined ? String(nutrient.upperLimit) : "");
  }
  const save = async () => {
    const t = target.trim() === "" ? null : Number(target);
    const u = upper.trim() === "" ? null : Number(upper);
    if ((t !== null && !(t >= 0)) || (u !== null && !(u >= 0))) {
      toast.error("Enter numbers of 0 or more");
      return;
    }
    try {
      await update.mutateAsync([{ code: nutrient!.code, target: t, upperLimit: u }]);
      toast.success("Target saved");
      onClose();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };
  return (
    <Dialog open={!!nutrient} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{nutrient?.name}</DialogTitle>
          <DialogDescription>
            Set your own values, for example ones from your doctor or dietitian. Leave a field empty to use the reference value.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={`Daily target (${unitLabel(nutrient?.unit)})`} htmlFor="ct-target">
            <Input id="ct-target" type="number" step="any" min="0" value={target} onChange={(e) => setTarget(e.target.value)} placeholder="Reference value" />
          </Field>
          <Field label={`Upper limit (${unitLabel(nutrient?.unit)})`} htmlFor="ct-upper">
            <Input id="ct-upper" type="number" step="any" min="0" value={upper} onChange={(e) => setUpper(e.target.value)} placeholder="Reference value" />
          </Field>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={save} disabled={update.isPending}>
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

const CATEGORY_LABELS: Record<string, string> = {
  ENERGY: "Energy",
  MACRONUTRIENT: "Macronutrients",
  FATTY_ACID: "Fats and fatty acids",
  VITAMIN: "Vitamins",
  MINERAL: "Minerals",
  OTHER: "Other",
};

function NutrientTargetsCard() {
  const targets = useTargets();
  const reset = useResetTarget();
  const [editing, setEditing] = useState<NutrientTarget | null>(null);
  const [confirmResetAll, setConfirmResetAll] = useState(false);
  if (targets.isLoading) return <Skeleton className="h-64" />;
  if (targets.error) return <ErrorState message={errorMessage(targets.error)} onRetry={() => targets.refetch()} />;
  const t = targets.data!;
  const anyCustom = t.nutrients.some((n) => n.customized);
  const categories = Object.keys(CATEGORY_LABELS).filter((c) => t.nutrients.some((n) => n.category === c && c !== "ENERGY"));

  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-start justify-between gap-2">
        <div>
          <CardTitle>Nutrient targets</CardTitle>
          <CardDescription>
            Reference system: NASEM Dietary Reference Intakes{t.age ? `, age ${t.age}` : ""}
            {t.sex ? `, ${t.sex.toLowerCase()}` : ""}. RDA = recommended intake, AI = adequate intake.
          </CardDescription>
        </div>
        {anyCustom && (
          <Button variant="outline" size="sm" onClick={() => setConfirmResetAll(true)}>
            Reset all custom values
          </Button>
        )}
      </CardHeader>
      <CardContent className="grid gap-6">
        {categories.map((c) => (
          <section key={c} className="overflow-x-auto">
            <h3 className="mb-1 text-sm font-semibold">{CATEGORY_LABELS[c]}</h3>
            <table className="w-full min-w-[560px] text-sm">
              <tbody>
                {t.nutrients
                  .filter((n) => n.category === c)
                  .map((n) => (
                    <tr key={n.code} className="border-b last:border-0">
                      <th scope="row" className="py-1.5 pr-2 text-left font-normal">
                        {n.name}
                        {n.notes.length > 0 && <span className="block text-xs text-muted-foreground">{n.notes[0]}</span>}
                      </th>
                      <td className="py-1.5 text-right tabular-nums">
                        {n.target === undefined ? <span className="text-muted-foreground">—</span> : amountWithUnit(n.target, n.unit)}
                        {n.targetType && <span className="ml-1 text-xs text-muted-foreground">{typeLabel(n.targetType)}</span>}
                      </td>
                      <td className="py-1.5 text-right tabular-nums">
                        {n.upperLimit === undefined ? (
                          <span className="text-muted-foreground">—</span>
                        ) : (
                          <>
                            ≤ {amountWithUnit(n.upperLimit, n.unit)}
                            <span className="ml-1 text-xs text-muted-foreground">
                              {typeLabel(n.upperLimitType)}
                              {!n.upperLimitComparable && " (supplements only)"}
                            </span>
                          </>
                        )}
                      </td>
                      <td className="py-1.5 pl-2 text-right">
                        <div className="flex justify-end gap-1">
                          {n.customized && <Badge variant="outline">Custom</Badge>}
                          <Button variant="ghost" size="sm" onClick={() => setEditing(n)} aria-label={`Edit target for ${n.name}`}>
                            Edit
                          </Button>
                          {n.customized && (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => reset.mutate(n.code, { onSuccess: () => toast.success("Reset to reference value"), onError: (e) => toast.error(errorMessage(e)) })}
                            >
                              Reset
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </section>
        ))}
        <p className="text-xs text-muted-foreground">{t.disclaimer} Pregnancy and breastfeeding values aren't included; ask your doctor for those.</p>
      </CardContent>
      <EditTargetDialog nutrient={editing} onClose={() => setEditing(null)} />
      <ConfirmDialog
        open={confirmResetAll}
        onOpenChange={setConfirmResetAll}
        title="Reset all custom targets?"
        description="Every nutrient goes back to its reference value. Your calorie and macro targets are not affected."
        confirmLabel="Reset all"
        pending={reset.isPending}
        onConfirm={() =>
          reset.mutate(null, {
            onSuccess: () => {
              toast.success("Custom targets removed");
              setConfirmResetAll(false);
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </Card>
  );
}

function ReferenceValuesCard() {
  const refs = useReferenceValues();
  const [code, setCode] = useState("");
  if (refs.isLoading) return <Skeleton className="h-40" />;
  if (refs.error) return <ErrorState message={errorMessage(refs.error)} />;
  const values = refs.data!.values;
  const codes = Array.from(new Set(values.map((v) => v.nutrientCode)));
  const shown = values.filter((v) => !code || v.nutrientCode === code);
  const sources = Array.from(new Map(values.map((v) => [v.sourceName, v])).values());
  return (
    <Card>
      <CardHeader>
        <CardTitle>Reference values</CardTitle>
        <CardDescription>The full table NutriTrack picks targets from, with sources.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        <ul className="grid gap-1 text-xs text-muted-foreground">
          {sources.map((s) => (
            <li key={s.sourceName}>
              {s.sourceUrl ? (
                <a className="text-primary hover:underline" href={s.sourceUrl} target="_blank" rel="noreferrer noopener">
                  {s.sourceName}
                </a>
              ) : (
                s.sourceName
              )}{" "}
              · {s.sourceVersion}
            </li>
          ))}
        </ul>
        <NativeSelect aria-label="Nutrient" value={code} onChange={(e) => setCode(e.target.value)} className="sm:w-64">
          <option value="">All nutrients</option>
          {codes.map((c) => (
            <option key={c} value={c}>
              {c.replaceAll("_", " ").toLowerCase()}
            </option>
          ))}
        </NativeSelect>
        <div className="max-h-96 overflow-auto">
          <table className="w-full min-w-[560px] text-sm">
            <thead className="sticky top-0 bg-card">
              <tr className="border-b text-left text-xs text-muted-foreground">
                <th className="py-2 font-medium">Nutrient</th>
                <th className="py-2 font-medium">Group</th>
                <th className="py-2 font-medium">Type</th>
                <th className="py-2 text-right font-medium">Amount</th>
                <th className="py-2 font-medium">Applies to</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((v, i) => (
                <tr key={`${v.nutrientCode}-${v.valueType}-${v.sex}-${v.ageMin}-${i}`} className="border-b last:border-0" title={v.notes}>
                  <td className="py-1">{v.nutrientCode.replaceAll("_", " ").toLowerCase()}</td>
                  <td className="py-1 text-muted-foreground">
                    {v.sex === "ANY" ? "All" : v.sex.toLowerCase()}, {v.ageMin}
                    {v.ageMax >= 120 ? "+" : `-${v.ageMax}`}
                  </td>
                  <td className="py-1">{typeLabel(v.valueType)}</td>
                  <td className="py-1 text-right tabular-nums">
                    {fmt(v.amount, 2)}
                    {v.valueType === "LIMIT_PCT_ENERGY" && "% of kcal"}
                  </td>
                  <td className="py-1 text-xs text-muted-foreground">{v.appliesTo.replaceAll("_", " ").toLowerCase()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}

const pwSchema = z
  .object({ currentPassword: z.string().min(1, "Enter your current password"), newPassword: passwordRule, confirm: z.string() })
  .refine((v) => v.newPassword === v.confirm, { path: ["confirm"], message: "Passwords don't match" });

function AccountTab() {
  const { applyAuth, clearSession, logout } = useAuth();
  const navigate = useNavigate();
  const form = useForm<z.infer<typeof pwSchema>>({ resolver: zodResolver(pwSchema), defaultValues: { currentPassword: "", newPassword: "", confirm: "" } });
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState("");
  const [deleting, setDeleting] = useState(false);
  const { theme, setTheme } = useTheme();

  const changePassword = form.handleSubmit(async (v) => {
    try {
      const auth = await api<AuthResponse>("/api/account/password", { method: "PUT", body: { currentPassword: v.currentPassword, newPassword: v.newPassword } });
      applyAuth(auth);
      form.reset();
      toast.success("Password changed. Other devices have been signed out.");
    } catch (err) {
      toast.error(errorMessage(err));
    }
  });

  const deleteAccount = async () => {
    setDeleting(true);
    try {
      await api<void>("/api/account", { method: "DELETE", body: { password: deletePassword } });
      clearSession();
      toast.success("Your account and all its data have been deleted");
      navigate("/", { replace: true });
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setDeleting(false);
    }
  };

  const themes: { value: Theme; label: string; icon: typeof Sun }[] = [
    { value: "light", label: "Light", icon: Sun },
    { value: "dark", label: "Dark", icon: Moon },
    { value: "system", label: "System", icon: Monitor },
  ];

  return (
    <div className="grid gap-4">
      <Card>
        <CardHeader>
          <CardTitle>Appearance</CardTitle>
        </CardHeader>
        <CardContent>
          <div role="radiogroup" aria-label="Theme" className="flex gap-2">
            {themes.map(({ value, label, icon: Icon }) => (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={theme === value}
                onClick={() => setTheme(value)}
                className={cn("flex items-center gap-2 rounded-lg border px-4 py-2 text-sm", theme === value && "border-primary bg-primary/10 text-primary")}
              >
                <Icon className="size-4" aria-hidden /> {label}
              </button>
            ))}
          </div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Change password</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={changePassword} className="grid gap-4 sm:grid-cols-3 sm:items-start" noValidate>
            <Field label="Current password" htmlFor="pw-current" error={form.formState.errors.currentPassword?.message}>
              <Input id="pw-current" type="password" autoComplete="current-password" {...form.register("currentPassword")} />
            </Field>
            <Field label="New password" htmlFor="pw-new" error={form.formState.errors.newPassword?.message}>
              <Input id="pw-new" type="password" autoComplete="new-password" {...form.register("newPassword")} />
            </Field>
            <Field label="Confirm new password" htmlFor="pw-confirm" error={form.formState.errors.confirm?.message}>
              <Input id="pw-confirm" type="password" autoComplete="new-password" {...form.register("confirm")} />
            </Field>
            <div className="sm:col-span-3">
              <Button type="submit" disabled={form.formState.isSubmitting}>
                Change password
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Session</CardTitle>
        </CardHeader>
        <CardContent>
          <Button
            variant="outline"
            onClick={async () => {
              await logout();
              navigate("/login", { replace: true });
            }}
          >
            Log out
          </Button>
        </CardContent>
      </Card>
      <Card className="border-destructive/40">
        <CardHeader>
          <CardTitle className="text-destructive">Delete account</CardTitle>
          <CardDescription>Permanently deletes your profile, diary, recipes, custom foods, weight and water logs. This can't be undone.</CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
            Delete my account
          </Button>
        </CardContent>
      </Card>
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={(o) => {
          setDeleteOpen(o);
          if (!o) setDeletePassword("");
        }}
        title="Delete your account?"
        description="Enter your password to confirm. All your data will be deleted immediately."
        confirmLabel="Delete everything"
        pending={deleting || !deletePassword}
        onConfirm={deleteAccount}
      >
        <Field label="Password" htmlFor="delete-password">
          <Input id="delete-password" type="password" autoComplete="current-password" value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} />
        </Field>
      </ConfirmDialog>
    </div>
  );
}

export default function SettingsPage() {
  const profile = useProfile();
  if (profile.isLoading) return <PageSkeleton />;
  if (profile.error || !profile.data) return <ErrorState message={errorMessage(profile.error)} onRetry={() => profile.refetch()} />;
  return (
    <>
      <PageHeader title="Profile & settings" />
      <Tabs defaultValue="profile">
        <TabsList>
          <TabsTrigger value="profile">Profile</TabsTrigger>
          <TabsTrigger value="targets">Targets</TabsTrigger>
          <TabsTrigger value="account">Account</TabsTrigger>
        </TabsList>
        <TabsContent value="profile">
          <ProfileTab profile={profile.data} />
        </TabsContent>
        <TabsContent value="targets" className="grid gap-4">
          <MacroTargetsCard key={profile.data.calorieTargetKcal ?? "none"} profile={profile.data} />
          <NutrientTargetsCard />
          <ReferenceValuesCard />
        </TabsContent>
        <TabsContent value="account">
          <AccountTab />
        </TabsContent>
      </Tabs>
    </>
  );
}
