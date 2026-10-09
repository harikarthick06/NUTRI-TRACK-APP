import { Link } from "react-router-dom";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { Droplets, Plus, Pill, Utensils } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/empty-state";
import { PageSkeleton } from "@/components/ui/skeleton";
import { Progress } from "@/components/ui/progress";
import { AddFoodDialog } from "@/components/nutrition/AddFoodDialog";
import { DateNav } from "@/components/nutrition/DateNav";
import { MacroCard } from "@/components/nutrition/MacroCard";
import { ProgressRing } from "@/components/nutrition/ProgressRing";
import { useDailyNutrition, useDiary, useProfile, useWaterActions, useWaterDay } from "@/hooks/queries";
import { errorMessage } from "@/lib/api";
import { todayIso } from "@/lib/dates";
import { useDateParam } from "@/hooks/useDateParam";
import { MEAL_LABELS, type MealType } from "@/lib/types";
import { fmt, pct } from "@/lib/utils";

export default function DashboardPage() {
  const [date, setDate] = useDateParam();
  const diary = useDiary(date);
  const profile = useProfile();
  const nutrition = useDailyNutrition(date);
  const water = useWaterDay(date);
  const waterActions = useWaterActions();
  const [addOpen, setAddOpen] = useState(false);
  const [addMeal, setAddMeal] = useState<MealType>("BREAKFAST");

  if (diary.isLoading || profile.isLoading) return <PageSkeleton />;
  if (diary.error) return <ErrorState message={errorMessage(diary.error)} onRetry={() => diary.refetch()} />;
  const day = diary.data!;
  const p = profile.data;
  const t = day.totals;
  const kcal = t.energyKcal.amount;
  const kcalTarget = p?.calorieTargetKcal;
  const remaining = kcalTarget !== undefined && kcal !== undefined ? kcalTarget - kcal : kcalTarget;

  const macroKcal = [
    { name: "Protein", value: (t.proteinG.amount ?? 0) * 4, color: "var(--protein)" },
    { name: "Carbs", value: (t.carbsG.amount ?? 0) * 4, color: "var(--carbs)" },
    { name: "Fat", value: (t.fatG.amount ?? 0) * 9, color: "var(--fat)" },
  ];
  const macroSum = macroKcal.reduce((s, m) => s + m.value, 0);
  const macrosComplete = [t.proteinG, t.carbsG, t.fatG].every((m) => m.entriesWithData === m.entries);

  const micro = nutrition.data?.nutrients.filter((n) => ["VITAMIN", "MINERAL"].includes(n.category)) ?? [];
  const microWithData = micro.filter((n) => n.amount !== undefined).length;
  const aboveUl = nutrition.data?.nutrients.filter((n) => n.status === "ABOVE_UPPER_LIMIT") ?? [];

  const openAdd = (meal: MealType) => {
    setAddMeal(meal);
    setAddOpen(true);
  };

  const quickWater = async (ml: number) => {
    try {
      await waterActions.add.mutateAsync({ date, amountMl: ml });
      toast.success(`Added ${ml} mL of water`);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <>
      <PageHeader
        title={`Hello, ${p?.name?.split(" ")[0] ?? "there"}`}
        description="Your nutrition for the day, from the foods you've logged."
        actions={
          <>
            <DateNav date={date} onChange={setDate} />
            <Button onClick={() => openAdd("BREAKFAST")}>
              <Plus /> Add food
            </Button>
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:row-span-2">
          <CardHeader>
            <CardTitle>Calories</CardTitle>
            <CardDescription>{day.entryCount} foods logged</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col items-center gap-4">
            <ProgressRing value={pct(kcal, kcalTarget)} size={168} stroke={14} label={`${fmt(kcal)} of ${fmt(kcalTarget)} kcal`}>
              <div>
                <p className="text-3xl font-semibold tabular-nums">{fmt(kcal)}</p>
                <p className="text-xs text-muted-foreground">of {kcalTarget ? `${fmt(kcalTarget)} kcal` : "no target"}</p>
              </div>
            </ProgressRing>
            <dl className="grid w-full grid-cols-3 gap-2 text-center text-sm">
              <div>
                <dt className="text-muted-foreground">Eaten</dt>
                <dd className="font-semibold tabular-nums">{fmt(kcal)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Target</dt>
                <dd className="font-semibold tabular-nums">{fmt(kcalTarget)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">{remaining !== undefined && remaining < 0 ? "Over" : "Remaining"}</dt>
                <dd className="font-semibold tabular-nums">{fmt(remaining !== undefined ? Math.abs(remaining) : undefined)}</dd>
              </div>
            </dl>
            {t.energyKcal.entries > t.energyKcal.entriesWithData && (
              <p className="text-xs text-warning-foreground dark:text-warning">
                {t.energyKcal.entries - t.energyKcal.entriesWithData} foods have no calorie data and aren't counted.
              </p>
            )}
            {!kcalTarget && (
              <Button variant="link" asChild>
                <Link to="/settings">Set a calorie target</Link>
              </Button>
            )}
          </CardContent>
        </Card>

        <div className="grid gap-4 sm:grid-cols-2 lg:col-span-2">
          <MacroCard label="Protein" total={t.proteinG} target={p?.proteinTargetG} unit="g" colorClass="bg-protein" />
          <MacroCard label="Carbohydrate" total={t.carbsG} target={p?.carbsTargetG} unit="g" colorClass="bg-carbs" />
          <MacroCard label="Fat" total={t.fatG} target={p?.fatTargetG} unit="g" colorClass="bg-fat" />
          <MacroCard label="Fibre" total={t.fiberG} target={p?.fiberTargetG} unit="g" colorClass="bg-fiber" />
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Where calories came from</CardTitle>
            <CardDescription>Protein and carbs at 4 kcal/g, fat at 9 kcal/g</CardDescription>
          </CardHeader>
          <CardContent>
            {macroSum > 0 ? (
              <div className="flex items-center gap-4">
                <div className="h-32 w-32">
                  <ResponsiveContainer>
                    <PieChart>
                      <Pie data={macroKcal} dataKey="value" innerRadius={36} outerRadius={60} paddingAngle={2} stroke="none">
                        {macroKcal.map((m) => (
                          <Cell key={m.name} fill={m.color} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(raw) => { const v = Number(raw); return `${fmt(v)} kcal`; }} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <ul className="grid gap-1 text-sm">
                  {macroKcal.map((m) => (
                    <li key={m.name} className="flex items-center gap-2">
                      <span className="size-3 rounded-full" style={{ background: m.color }} aria-hidden />
                      {m.name} <span className="font-medium tabular-nums">{fmt((m.value / macroSum) * 100)}%</span>
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">Log a meal to see your macro split.</p>
            )}
            {macroSum > 0 && !macrosComplete && (
              <p className="mt-2 text-xs text-muted-foreground">Some foods are missing macro data, so this split is approximate.</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <div>
              <CardTitle className="flex items-center gap-2">
                <Droplets className="size-4 text-water" aria-hidden /> Water
              </CardTitle>
              <CardDescription>
                {fmt(water.data?.totalMl)} mL{water.data?.targetMl ? ` of ${fmt(water.data.targetMl)} mL` : ""}
              </CardDescription>
            </div>
            <Button variant="ghost" size="sm" asChild>
              <Link to="/water">Details</Link>
            </Button>
          </CardHeader>
          <CardContent className="grid gap-3">
            <Progress value={water.data?.percentOfTarget ?? null} indicatorClassName="bg-water" label="Water progress" />
            <div className="flex gap-2">
              {[250, 500].map((ml) => (
                <Button key={ml} variant="outline" size="sm" onClick={() => quickWater(ml)} disabled={waterActions.add.isPending}>
                  +{ml} mL
                </Button>
              ))}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <div>
              <CardTitle className="flex items-center gap-2">
                <Pill className="size-4 text-primary" aria-hidden /> Vitamins & minerals
              </CardTitle>
              <CardDescription>
                {day.entryCount ? `${microWithData} of ${micro.length} have data today` : "Nothing logged yet"}
              </CardDescription>
            </div>
            <Button variant="ghost" size="sm" asChild>
              <Link to={`/nutrients${date !== todayIso() ? `?date=${date}` : ""}`}>View all</Link>
            </Button>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {aboveUl.length > 0 ? (
              <p className="rounded-md bg-destructive/10 p-2 text-destructive">
                Above the upper limit: {aboveUl.map((n) => n.name).join(", ")}
              </p>
            ) : (
              <p className="text-muted-foreground">No nutrient is above its upper limit today.</p>
            )}
          </CardContent>
        </Card>
      </div>

      <h2 className="mb-3 mt-8 text-lg font-semibold">Meals</h2>
      <div className="grid gap-3 md:grid-cols-2">
        {day.meals.map((m) => (
          <Card key={m.mealType}>
            <CardHeader className="flex-row items-center justify-between pb-2">
              <div>
                <CardTitle>{MEAL_LABELS[m.mealType]}</CardTitle>
                <CardDescription className="tabular-nums">
                  {fmt(m.totals.energyKcal.amount)} kcal · P {fmt(m.totals.proteinG.amount, 1)} g · C {fmt(m.totals.carbsG.amount, 1)} g · F{" "}
                  {fmt(m.totals.fatG.amount, 1)} g · Fibre {fmt(m.totals.fiberG.amount, 1)} g
                </CardDescription>
              </div>
              <Button variant="ghost" size="icon-sm" onClick={() => openAdd(m.mealType)} aria-label={`Add food to ${MEAL_LABELS[m.mealType]}`}>
                <Plus />
              </Button>
            </CardHeader>
            <CardContent>
              {m.entries.length === 0 ? (
                <p className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Utensils className="size-4" aria-hidden /> Nothing logged
                </p>
              ) : (
                <ul className="grid gap-1 text-sm">
                  {m.entries.map((e) => (
                    <li key={e.id} className="flex justify-between gap-2">
                      <span className="truncate">
                        {e.foodName}{" "}
                        <span className="text-muted-foreground">
                          · {fmt(e.quantity, 2)} {e.portionLabel ?? e.unit.toLowerCase()}
                        </span>
                      </span>
                      <span className="shrink-0 tabular-nums text-muted-foreground">{fmt(e.energyKcal)} kcal</span>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      <AddFoodDialog open={addOpen} onOpenChange={setAddOpen} date={date} meal={addMeal} />
    </>
  );
}
