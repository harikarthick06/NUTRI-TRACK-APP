import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input, NativeSelect } from "@/components/ui/input";
import { Field } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { useRecipeAction } from "@/hooks/queries";
import { errorMessage } from "@/lib/api";
import { todayIso } from "@/lib/dates";
import { MEAL_LABELS, MEAL_TYPES, type MealType } from "@/lib/types";
import { fmt } from "@/lib/utils";

export function LogRecipeDialog({
  recipe,
  onOpenChange,
}: {
  recipe: { id: number; name: string; servings: number; servingLabel?: string; servingWeightG?: number } | null;
  onOpenChange: (open: boolean) => void;
}) {
  const { log } = useRecipeAction();
  const [date, setDate] = useState(todayIso());
  const [meal, setMeal] = useState<MealType>("LUNCH");
  const [servings, setServings] = useState("1");
  const [whole, setWhole] = useState(false);

  const submit = async () => {
    if (!recipe) return;
    const n = Number(servings);
    if (!whole && !(n > 0)) {
      toast.error("Enter how many servings");
      return;
    }
    try {
      await log.mutateAsync({ id: recipe.id, date, mealType: meal, servings: whole ? 1 : n, wholeRecipe: whole });
      toast.success(`Logged ${recipe.name}`);
      onOpenChange(false);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <Dialog open={!!recipe} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Log {recipe?.name}</DialogTitle>
          <DialogDescription>
            {recipe?.servingLabel ?? "1 serving"}
            {recipe?.servingWeightG ? ` ≈ ${fmt(recipe.servingWeightG)} g` : ""} · makes {fmt(recipe?.servings, 1)} servings
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Date" htmlFor="log-date">
            <Input id="log-date" type="date" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} />
          </Field>
          <Field label="Meal" htmlFor="log-meal">
            <NativeSelect id="log-meal" value={meal} onChange={(e) => setMeal(e.target.value as MealType)}>
              {MEAL_TYPES.map((m) => (
                <option key={m} value={m}>
                  {MEAL_LABELS[m]}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <div className="flex items-center justify-between gap-3 rounded-lg border p-3 sm:col-span-2">
            <span className="text-sm">I ate the whole recipe</span>
            <Switch checked={whole} onCheckedChange={setWhole} aria-label="Whole recipe" />
          </div>
          {!whole && (
            <Field label="Servings" htmlFor="log-servings">
              <Input id="log-servings" type="number" step="0.25" min="0" value={servings} onChange={(e) => setServings(e.target.value)} />
            </Field>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={log.isPending}>
            Log
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
