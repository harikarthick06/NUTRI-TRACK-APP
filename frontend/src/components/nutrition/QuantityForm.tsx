import { useMemo, useState } from "react";
import { Info } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/label";
import { Input, NativeSelect } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { MEAL_LABELS, MEAL_TYPES, type FoodDetail, type MealType, type WeightState } from "@/lib/types";
import { fmt } from "@/lib/utils";
import { CompletenessBadge, DataQualityBadge } from "./badges";
import { basisAmount, canUseCookedWeight, defaultUnitOption, scaled, unitOptions } from "./quantity";

export interface QuantityValue {
  quantity: number;
  unit: string;
  portionId: number | null;
  weightState: WeightState;
  mealType: MealType;
  notes?: string;
}

export function QuantityForm({
  food,
  initial,
  showMeal = true,
  showNotes = true,
  submitLabel,
  pending,
  onSubmit,
  onBack,
}: {
  food: FoodDetail;
  initial?: Partial<QuantityValue>;
  showMeal?: boolean;
  showNotes?: boolean;
  submitLabel: string;
  pending?: boolean;
  onSubmit: (v: QuantityValue) => void;
  onBack?: () => void;
}) {
  const options = useMemo(() => unitOptions(food), [food]);
  const initialOption =
    (initial?.portionId && options.find((o) => o.portionId === initial.portionId)) ||
    (initial?.unit && options.find((o) => o.value === initial.unit)) ||
    defaultUnitOption(food);
  const [quantity, setQuantity] = useState(String(initial?.quantity ?? (initialOption?.portionId ? 1 : 100)));
  const [optionValue, setOptionValue] = useState(initialOption?.value ?? "G");
  const [cooked, setCooked] = useState(initial?.weightState === "COOKED_WEIGHT_OF_RAW_FOOD");
  const [meal, setMeal] = useState<MealType>(initial?.mealType ?? "BREAKFAST");
  const [notes, setNotes] = useState(initial?.notes ?? "");
  const [error, setError] = useState<string | null>(null);

  const cookedAllowed = canUseCookedWeight(food);
  const option = options.find((o) => o.value === optionValue) ?? options[0];
  const visibleOptions = cooked ? options.filter((o) => o.value === "G" || o.value === "KG") : options;
  const q = Number(quantity);
  const weightState: WeightState = cooked ? "COOKED_WEIGHT_OF_RAW_FOOD" : "AS_DEFINED";
  const basis = option ? basisAmount(food, q, option, weightState) : null;

  const preview = [
    { label: "Energy", value: scaled(food, "ENERGY_KCAL", basis), unit: "kcal", digits: 0 },
    { label: "Protein", value: scaled(food, "PROTEIN", basis), unit: "g", digits: 1 },
    { label: "Carbs", value: scaled(food, "CARBOHYDRATE", basis), unit: "g", digits: 1 },
    { label: "Fat", value: scaled(food, "FAT_TOTAL", basis), unit: "g", digits: 1 },
  ];

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!(q > 0) || q > 100000) {
      setError("Enter a quantity greater than zero");
      return;
    }
    if (!option) {
      setError("Choose a unit");
      return;
    }
    setError(null);
    onSubmit({
      quantity: q,
      unit: option.unit,
      portionId: option.portionId ?? null,
      weightState,
      mealType: meal,
      notes: notes.trim() || undefined,
    });
  };

  return (
    <form onSubmit={submit} className="grid gap-4" noValidate>
      <div>
        <p className="font-semibold">{food.name}</p>
        <p className="text-sm text-muted-foreground">{[food.brand, food.category].filter(Boolean).join(" · ")}</p>
        <div className="mt-1 flex flex-wrap gap-1">
          <DataQualityBadge quality={food.dataQuality} />
          <CompletenessBadge pct={food.completenessPct} />
        </div>
      </div>

      {cookedAllowed && (
        <div className="flex items-start justify-between gap-3 rounded-lg border p-3">
          <div>
            <p className="text-sm font-medium">I weighed it after cooking</p>
            <p className="text-xs text-muted-foreground">
              This food's values are for {food.preparationState.toLowerCase()} weight. We'll convert your cooked weight using a
              yield of ×{fmt(food.cookedYieldFactor, 2)} (estimated).
            </p>
          </div>
          <Switch
            checked={cooked}
            onCheckedChange={(v) => {
              setCooked(v);
              if (v) setOptionValue("G");
            }}
            aria-label="Weighed after cooking"
          />
        </div>
      )}
      {!cookedAllowed && food.preparationState === "RAW" && (
        <p className="flex gap-2 rounded-lg bg-muted p-3 text-xs text-muted-foreground">
          <Info className="size-4 shrink-0" aria-hidden />
          Values are for the raw food. If you weighed it cooked, search for a cooked version instead.
        </p>
      )}

      <div className="grid grid-cols-2 gap-3">
        <Field label={cooked ? "Cooked weight" : "Quantity"} htmlFor="qty" error={error ?? undefined}>
          <Input
            id="qty"
            type="number"
            inputMode="decimal"
            min="0"
            step="any"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            aria-invalid={!!error}
          />
        </Field>
        <Field label="Unit" htmlFor="unit">
          <NativeSelect id="unit" value={optionValue} onChange={(e) => setOptionValue(e.target.value)}>
            {visibleOptions.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </NativeSelect>
        </Field>
      </div>

      {showMeal && (
        <Field label="Meal" htmlFor="meal">
          <NativeSelect id="meal" value={meal} onChange={(e) => setMeal(e.target.value as MealType)}>
            {MEAL_TYPES.map((m) => (
              <option key={m} value={m}>
                {MEAL_LABELS[m]}
              </option>
            ))}
          </NativeSelect>
        </Field>
      )}

      <div className="rounded-lg bg-muted/60 p-3">
        <p className="mb-2 text-xs text-muted-foreground">
          {basis === null
            ? "This unit can't be converted for this food. Use grams or one of the listed portions."
            : `${fmt(basis, 1)} ${food.basisUnit === "G" ? "g" : "mL"}${cooked ? " raw" : ""}`}
        </p>
        <dl className="grid grid-cols-4 gap-2 text-center">
          {preview.map((p) => (
            <div key={p.label}>
              <dt className="text-xs text-muted-foreground">{p.label}</dt>
              <dd className="font-semibold tabular-nums">
                {p.value === null ? <span title="No data for this food">—</span> : `${fmt(p.value, p.digits)} ${p.unit}`}
              </dd>
            </div>
          ))}
        </dl>
      </div>

      {showNotes && (
        <Field label="Notes (optional)" htmlFor="notes">
          <Input id="notes" value={notes} maxLength={500} onChange={(e) => setNotes(e.target.value)} />
        </Field>
      )}

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        {onBack && (
          <Button variant="outline" onClick={onBack}>
            Back
          </Button>
        )}
        <Button type="submit" disabled={pending || basis === null}>
          {pending ? "Saving…" : submitLabel}
        </Button>
      </div>
    </form>
  );
}
