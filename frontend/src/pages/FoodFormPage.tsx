import { useMemo } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { Plus, Trash2 } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/empty-state";
import { Input, NativeSelect, Textarea } from "@/components/ui/input";
import { Field } from "@/components/ui/label";
import { PageSkeleton } from "@/components/ui/skeleton";
import { useFood, useNutrients, useSaveCustomFood } from "@/hooks/queries";
import { ApiError, errorMessage } from "@/lib/api";
import type { CustomFoodRequest, FoodDetail, NutrientDef, PreparationState } from "@/lib/types";
import { unitLabel } from "@/lib/utils";

const numberString = z
  .string()
  .trim()
  .refine((v) => v === "" || (!Number.isNaN(Number(v)) && Number(v) >= 0), "Enter a number of 0 or more");

const schema = z.object({
  name: z.string().trim().min(1, "Enter a name").max(255),
  brand: z.string().max(255),
  category: z.string().max(100),
  preparationState: z.enum(["NOT_APPLICABLE", "RAW", "COOKED", "DRY"]),
  basisQuantity: z.string().refine((v) => Number(v) > 0, "Must be more than 0"),
  basisUnit: z.enum(["G", "ML"]),
  barcode: z.string().trim().refine((v) => v === "" || /^\d{8,14}$/.test(v), "8-14 digits"),
  notes: z.string().max(1000),
  nutrients: z.record(numberString),
  portions: z.array(
    z.object({
      label: z.string().trim().min(1, "Name the portion").max(100),
      unit: z.enum(["PIECE", "CUP", "TBSP", "TSP", "SERVING", "SLICE", "BOWL"]),
      unitQuantity: z.string().refine((v) => Number(v) > 0, "Must be more than 0"),
      basisAmount: z.string().refine((v) => Number(v) > 0, "Must be more than 0"),
    }),
  ),
});
type Values = z.infer<typeof schema>;

const CATEGORY_LABELS: Record<string, string> = {
  ENERGY: "Energy",
  MACRONUTRIENT: "Macronutrients",
  FATTY_ACID: "Fats and fatty acids",
  VITAMIN: "Vitamins",
  MINERAL: "Minerals",
  OTHER: "Other",
};

function defaults(food?: FoodDetail): Values {
  const nutrients: Record<string, string> = {};
  for (const n of food?.nutrients ?? []) {
    if (n.known && n.amount !== undefined) nutrients[n.code] = String(n.amount);
  }
  return {
    name: food?.name ?? "",
    brand: food?.brand ?? "",
    category: food?.category ?? "",
    preparationState: food?.preparationState ?? "NOT_APPLICABLE",
    basisQuantity: String(food?.basisQuantity ?? 100),
    basisUnit: food?.basisUnit ?? "G",
    barcode: food?.barcode ?? "",
    notes: food?.notes ?? "",
    nutrients,
    portions: (food?.portions ?? []).map((p) => ({
      label: p.label,
      unit: p.unit as Values["portions"][number]["unit"],
      unitQuantity: String(p.unitQuantity),
      basisAmount: String(p.basisAmount),
    })),
  };
}

export default function FoodFormPage() {
  const { id } = useParams();
  const foodId = id ? Number(id) : undefined;
  const food = useFood(foodId);
  const nutrients = useNutrients();
  if ((foodId && food.isLoading) || nutrients.isLoading) return <PageSkeleton />;
  if (foodId && (food.error || !food.data)) return <ErrorState message={errorMessage(food.error)} />;
  if (nutrients.error || !nutrients.data) return <ErrorState message={errorMessage(nutrients.error)} />;
  if (food.data && !(food.data.owned && food.data.foodType === "CUSTOM")) {
    return <ErrorState message="Only your own custom foods can be edited." />;
  }
  return <FoodForm key={foodId ?? "new"} food={food.data} defs={nutrients.data} />;
}

function FoodForm({ food, defs }: { food?: FoodDetail; defs: NutrientDef[] }) {
  const navigate = useNavigate();
  const save = useSaveCustomFood(food?.id);
  const { register, control, handleSubmit, formState, watch, setError } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: defaults(food),
  });
  const portions = useFieldArray({ control, name: "portions" });
  const basisUnit = watch("basisUnit");
  const basisQuantity = watch("basisQuantity");
  const groups = useMemo(() => {
    const order = ["ENERGY", "MACRONUTRIENT", "FATTY_ACID", "VITAMIN", "MINERAL", "OTHER"];
    return order.map((c) => ({ category: c, defs: defs.filter((d) => d.category === c) })).filter((g) => g.defs.length);
  }, [defs]);
  const errors = formState.errors;

  const onSubmit = async (v: Values) => {
    const values: Record<string, number> = {};
    for (const [code, raw] of Object.entries(v.nutrients)) {
      if (raw !== undefined && raw.trim() !== "") values[code] = Number(raw);
    }
    if (values.ENERGY_KCAL === undefined) {
      setError("nutrients.ENERGY_KCAL" as never, { message: "Energy is needed so the food can be counted" });
      return;
    }
    const body: CustomFoodRequest = {
      name: v.name.trim(),
      brand: v.brand.trim() || undefined,
      category: v.category.trim() || undefined,
      preparationState: v.preparationState as PreparationState,
      basisQuantity: Number(v.basisQuantity),
      basisUnit: v.basisUnit,
      nutrients: values,
      portions: v.portions.map((p) => ({ label: p.label.trim(), unit: p.unit, unitQuantity: Number(p.unitQuantity), basisAmount: Number(p.basisAmount) })),
      barcode: v.barcode.trim() || undefined,
      notes: v.notes.trim() || undefined,
    };
    try {
      const saved = await save.mutateAsync(body);
      toast.success(food ? "Food updated" : "Food created");
      navigate(`/foods/${saved.id}`, { replace: true });
    } catch (err) {
      toast.error(err instanceof ApiError && Object.keys(err.fieldErrors).length ? Object.values(err.fieldErrors)[0] : errorMessage(err));
    }
  };

  const nutrientError = (code: string) => (errors.nutrients as Record<string, { message?: string }> | undefined)?.[code]?.message;

  return (
    <>
      <PageHeader
        title={food ? `Edit ${food.name}` : "Create a food"}
        description="Copy values from the label or a trusted source. Leave a nutrient blank if you don't know it; blank is treated as unknown, not zero."
      />
      <form onSubmit={handleSubmit(onSubmit)} className="grid gap-4" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>Basics</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" htmlFor="name" error={errors.name?.message} className="sm:col-span-2">
              <Input id="name" {...register("name")} aria-invalid={!!errors.name} />
            </Field>
            <Field label="Brand (optional)" htmlFor="brand">
              <Input id="brand" {...register("brand")} />
            </Field>
            <Field label="Category (optional)" htmlFor="category">
              <Input id="category" placeholder="e.g. Snacks" {...register("category")} />
            </Field>
            <Field label="Raw or cooked?" htmlFor="preparationState" hint="Tells the diary whether a cooked weight needs converting">
              <NativeSelect id="preparationState" {...register("preparationState")}>
                <option value="NOT_APPLICABLE">Not applicable (ready to eat)</option>
                <option value="RAW">Raw</option>
                <option value="COOKED">Cooked</option>
                <option value="DRY">Dry (uncooked grain or dal)</option>
              </NativeSelect>
            </Field>
            <Field label="Barcode (optional)" htmlFor="barcode" error={errors.barcode?.message}>
              <Input id="barcode" inputMode="numeric" {...register("barcode")} />
            </Field>
            <div className="grid grid-cols-2 gap-3 sm:col-span-2">
              <Field label="Values are per" htmlFor="basisQuantity" error={errors.basisQuantity?.message}>
                <Input id="basisQuantity" type="number" step="any" {...register("basisQuantity")} />
              </Field>
              <Field label="Unit" htmlFor="basisUnit">
                <NativeSelect id="basisUnit" {...register("basisUnit")}>
                  <option value="G">grams</option>
                  <option value="ML">millilitres</option>
                </NativeSelect>
              </Field>
            </div>
            <Field label="Notes (optional)" htmlFor="notes" className="sm:col-span-2">
              <Textarea id="notes" rows={2} {...register("notes")} />
            </Field>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Nutrients</CardTitle>
            <CardDescription>
              Per {basisQuantity || "?"} {basisUnit === "G" ? "g" : "mL"}. Energy is required; everything else is optional.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-6">
            {groups.map((g) => (
              <fieldset key={g.category}>
                <legend className="mb-2 text-sm font-semibold">{CATEGORY_LABELS[g.category]}</legend>
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {g.defs.map((d) => (
                    <Field
                      key={d.code}
                      label={`${d.name} (${unitLabel(d.unit)})`}
                      htmlFor={`n-${d.code}`}
                      error={nutrientError(d.code)}
                      hint={d.formNote}
                    >
                      <Input
                        id={`n-${d.code}`}
                        type="number"
                        inputMode="decimal"
                        step="any"
                        min="0"
                        placeholder="Unknown"
                        {...register(`nutrients.${d.code}` as const)}
                      />
                    </Field>
                  ))}
                </div>
              </fieldset>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <div>
              <CardTitle>Portions</CardTitle>
              <CardDescription>Household measures like "1 piece" or "1 cup", with their weight.</CardDescription>
            </div>
            <Button variant="outline" size="sm" onClick={() => portions.append({ label: "", unit: "PIECE", unitQuantity: "1", basisAmount: "" })}>
              <Plus /> Add portion
            </Button>
          </CardHeader>
          <CardContent className="grid gap-3">
            {portions.fields.length === 0 && <p className="text-sm text-muted-foreground">No portions. You can still log by weight.</p>}
            {portions.fields.map((p, i) => (
              <div key={p.id} className="grid grid-cols-2 items-end gap-2 rounded-lg border p-3 sm:grid-cols-[2fr_1fr_1fr_1fr_auto]">
                <Field label="Label" htmlFor={`p-label-${i}`} error={errors.portions?.[i]?.label?.message} className="col-span-2 sm:col-span-1">
                  <Input id={`p-label-${i}`} placeholder="1 medium piece" {...register(`portions.${i}.label`)} />
                </Field>
                <Field label="Unit" htmlFor={`p-unit-${i}`}>
                  <NativeSelect id={`p-unit-${i}`} {...register(`portions.${i}.unit`)}>
                    {["PIECE", "SERVING", "SLICE", "BOWL", "CUP", "TBSP", "TSP"].map((u) => (
                      <option key={u} value={u}>
                        {u.toLowerCase()}
                      </option>
                    ))}
                  </NativeSelect>
                </Field>
                <Field label="How many" htmlFor={`p-qty-${i}`} error={errors.portions?.[i]?.unitQuantity?.message}>
                  <Input id={`p-qty-${i}`} type="number" step="any" {...register(`portions.${i}.unitQuantity`)} />
                </Field>
                <Field label={`Weighs (${basisUnit === "G" ? "g" : "mL"})`} htmlFor={`p-amt-${i}`} error={errors.portions?.[i]?.basisAmount?.message}>
                  <Input id={`p-amt-${i}`} type="number" step="any" {...register(`portions.${i}.basisAmount`)} />
                </Field>
                <Button variant="ghost" size="icon" onClick={() => portions.remove(i)} aria-label="Remove portion">
                  <Trash2 />
                </Button>
              </div>
            ))}
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => navigate(-1)}>
            Cancel
          </Button>
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? "Saving…" : food ? "Save changes" : "Create food"}
          </Button>
        </div>
      </form>
    </>
  );
}
