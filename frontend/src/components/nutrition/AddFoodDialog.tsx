import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { useAddEntry, useFood, useUpdateEntry } from "@/hooks/queries";
import { errorMessage } from "@/lib/api";
import { MEAL_LABELS, type DiaryEntry, type FoodDetail, type MealType } from "@/lib/types";
import { prettyDate } from "@/lib/dates";
import { FoodPicker } from "./FoodPicker";
import { QuantityForm, type QuantityValue } from "./QuantityForm";

/**
 * Add a food to the diary (search, then quantity), edit an existing entry, or pick an ingredient
 * (onPick mode, used by the recipe builder).
 */
export function AddFoodDialog({
  open,
  onOpenChange,
  date,
  meal,
  entry,
  initialFoodId,
  onPick,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  date: string;
  meal?: MealType;
  entry?: DiaryEntry | null;
  initialFoodId?: number | null;
  onPick?: (food: FoodDetail, value: QuantityValue) => void;
}) {
  const [foodId, setFoodId] = useState<number | null>(entry?.foodId ?? initialFoodId ?? null);
  const food = useFood(foodId);
  const add = useAddEntry();
  const update = useUpdateEntry();

  useEffect(() => {
    if (open) setFoodId(entry?.foodId ?? initialFoodId ?? null);
  }, [open, entry, initialFoodId]);

  const submit = async (v: QuantityValue) => {
    if (!food.data) return;
    if (onPick) {
      onPick(food.data, v);
      onOpenChange(false);
      return;
    }
    try {
      if (entry) {
        await update.mutateAsync({
          id: entry.id,
          body: { date: entry.date, mealType: v.mealType, quantity: v.quantity, unit: v.unit, portionId: v.portionId, weightState: v.weightState, notes: v.notes },
        });
        toast.success("Entry updated");
      } else {
        await add.mutateAsync({ foodId: food.data.id, date, ...v });
        toast.success(`Added ${food.data.name} to ${MEAL_LABELS[v.mealType].toLowerCase()}`);
      }
      onOpenChange(false);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const title = entry ? "Edit entry" : onPick ? "Add ingredient" : "Add food";
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{onPick ? "Search for an ingredient and enter how much goes in." : prettyDate(date)}</DialogDescription>
        </DialogHeader>
        {foodId === null ? (
          <FoodPicker autoFocus onSelect={(f) => setFoodId(f.id)} />
        ) : food.isLoading ? (
          <div className="grid gap-3">
            <Skeleton className="h-10" />
            <Skeleton className="h-24" />
          </div>
        ) : food.data ? (
          <QuantityForm
            key={food.data.id}
            food={food.data}
            showMeal={!onPick}
            showNotes={!onPick}
            initial={
              entry
                ? {
                    quantity: entry.quantity,
                    unit: entry.unit,
                    portionId: entry.portionId ?? null,
                    weightState: entry.weightState,
                    mealType: entry.mealType,
                    notes: entry.notes,
                  }
                : { mealType: meal ?? "BREAKFAST" }
            }
            submitLabel={entry ? "Save changes" : onPick ? "Add ingredient" : "Add to diary"}
            pending={add.isPending || update.isPending}
            onSubmit={submit}
            onBack={entry || initialFoodId ? undefined : () => setFoodId(null)}
          />
        ) : (
          <p className="text-sm text-destructive">{errorMessage(food.error)}</p>
        )}
      </DialogContent>
    </Dialog>
  );
}
