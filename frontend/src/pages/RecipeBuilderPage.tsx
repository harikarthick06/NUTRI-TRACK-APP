import { useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { AlertTriangle, Copy, Plus, Trash2 } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/empty-state";
import { Input, Textarea } from "@/components/ui/input";
import { Field } from "@/components/ui/label";
import { PageSkeleton, Skeleton } from "@/components/ui/skeleton";
import { AddFoodDialog } from "@/components/nutrition/AddFoodDialog";
import { DataQualityBadge } from "@/components/nutrition/badges";
import { useDebounced } from "@/components/nutrition/FoodPicker";
import { LogRecipeDialog } from "@/components/nutrition/LogRecipeDialog";
import { unitName } from "@/components/nutrition/quantity";
import { useRecipe, useRecipeAction, useRecipePreview, useSaveRecipe } from "@/hooks/queries";
import { errorMessage } from "@/lib/api";
import { todayIso } from "@/lib/dates";
import type { DataQuality, RecipeDetail, RecipeRequest, WeightState } from "@/lib/types";
import { amountWithUnit, fmt } from "@/lib/utils";

interface DraftIngredient {
  key: string;
  foodId: number;
  foodName: string;
  dataQuality: DataQuality;
  quantity: number;
  unit: string;
  unitLabel: string;
  portionId: number | null;
  weightState: WeightState;
}

const HEADLINE = ["ENERGY_KCAL", "PROTEIN", "CARBOHYDRATE", "FAT_TOTAL", "FIBER"];

export default function RecipeBuilderPage() {
  const { id } = useParams();
  const recipeId = id ? Number(id) : null;
  const recipe = useRecipe(recipeId);
  if (recipeId && recipe.isLoading) return <PageSkeleton />;
  if (recipeId && (recipe.error || !recipe.data)) return <ErrorState message={errorMessage(recipe.error)} onRetry={() => recipe.refetch()} />;
  return <Builder key={recipeId ?? "new"} recipe={recipe.data} />;
}

function Builder({ recipe }: { recipe?: RecipeDetail }) {
  const navigate = useNavigate();
  const readOnly = !!recipe && !recipe.owned;
  const save = useSaveRecipe(recipe?.owned ? recipe.id : undefined);
  const { duplicate } = useRecipeAction();
  const [name, setName] = useState(recipe?.name ?? "");
  const [description, setDescription] = useState(recipe?.description ?? "");
  const [instructions, setInstructions] = useState(recipe?.instructions ?? "");
  const [servings, setServings] = useState(String(recipe?.servings ?? 4));
  const [cookedWeight, setCookedWeight] = useState(recipe?.totalCookedWeightG ? String(recipe.totalCookedWeightG) : "");
  const [servingLabel, setServingLabel] = useState(recipe?.servingLabel ?? "");
  const [ingredients, setIngredients] = useState<DraftIngredient[]>(
    () =>
      recipe?.ingredients.map((i, idx) => ({
        key: `${i.id ?? idx}`,
        foodId: i.foodId,
        foodName: i.foodName,
        dataQuality: i.dataQuality,
        quantity: i.quantity,
        unit: i.unit,
        unitLabel: unitName(i.unit),
        portionId: i.portionId ?? null,
        weightState: i.weightState,
      })) ?? [],
  );
  const [picking, setPicking] = useState(false);
  const [logging, setLogging] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);

  const request: RecipeRequest | null = useMemo(() => {
    const s = Number(servings);
    if (!(s > 0) || ingredients.length === 0) return null;
    const cw = Number(cookedWeight);
    return {
      name: name.trim() || "Untitled recipe",
      description: description.trim() || undefined,
      instructions: instructions.trim() || undefined,
      servings: s,
      totalCookedWeightG: cookedWeight.trim() && cw > 0 ? cw : null,
      servingLabel: servingLabel.trim() || undefined,
      ingredients: ingredients.map((i) => ({
        foodId: i.foodId,
        quantity: i.quantity,
        unit: i.unit,
        portionId: i.portionId,
        weightState: i.weightState,
      })),
    };
  }, [name, description, instructions, servings, cookedWeight, servingLabel, ingredients]);

  const debounced = useDebounced(request, 400);
  const preview = useRecipePreview(debounced);
  const nutrition = preview.data ?? (readOnly ? recipe?.nutrition : undefined);

  const submit = async () => {
    if (!name.trim()) {
      setNameError("Give the recipe a name");
      return;
    }
    if (!request) {
      toast.error("Add at least one ingredient and a number of servings");
      return;
    }
    try {
      const saved = await save.mutateAsync({ ...request, name: name.trim() });
      toast.success(recipe ? "Recipe saved" : "Recipe created");
      navigate(`/recipes/${saved.id}`, { replace: true });
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const headline = HEADLINE.map((c) => nutrition?.nutrients.find((n) => n.code === c)).filter(Boolean);
  const others = nutrition?.nutrients.filter((n) => !HEADLINE.includes(n.code)) ?? [];

  return (
    <>
      <PageHeader
        title={recipe ? recipe.name : "New recipe"}
        description={readOnly ? "A built-in recipe. Duplicate it to change ingredients or amounts." : "Add ingredients as they go into the pot, then weigh the finished dish."}
        actions={
          <>
            {recipe && (
              <Button variant="outline" onClick={() => setLogging(true)}>
                Log to diary
              </Button>
            )}
            {readOnly ? (
              <Button
                disabled={duplicate.isPending}
                onClick={async () => {
                  try {
                    const copy = await duplicate.mutateAsync(recipe!.id);
                    toast.success("Copied to your recipes");
                    navigate(`/recipes/${copy.id}`);
                  } catch (err) {
                    toast.error(errorMessage(err));
                  }
                }}
              >
                <Copy /> Duplicate to edit
              </Button>
            ) : (
              <Button onClick={submit} disabled={save.isPending}>
                {save.isPending ? "Saving…" : "Save recipe"}
              </Button>
            )}
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="grid content-start gap-4 lg:col-span-3">
          <Card>
            <CardHeader>
              <CardTitle>Details</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <Field label="Name" htmlFor="r-name" error={nameError ?? undefined} className="sm:col-span-2">
                <Input id="r-name" value={name} disabled={readOnly} maxLength={200} onChange={(e) => { setName(e.target.value); setNameError(null); }} />
              </Field>
              <Field label="Servings" htmlFor="r-servings">
                <Input id="r-servings" type="number" min="0.1" step="0.5" value={servings} disabled={readOnly} onChange={(e) => setServings(e.target.value)} />
              </Field>
              <Field label="Serving name (optional)" htmlFor="r-label">
                <Input id="r-label" placeholder="e.g. 1 katori" value={servingLabel} disabled={readOnly} maxLength={100} onChange={(e) => setServingLabel(e.target.value)} />
              </Field>
              <Field
                label="Cooked weight of the whole dish (g)"
                htmlFor="r-cooked"
                className="sm:col-span-2"
                hint="Weigh the pot empty and full. Cooking adds or removes water, so without this the per-serving values assume no change."
              >
                <Input id="r-cooked" type="number" min="1" step="1" placeholder="Optional but recommended" value={cookedWeight} disabled={readOnly} onChange={(e) => setCookedWeight(e.target.value)} />
              </Field>
              <Field label="Description (optional)" htmlFor="r-desc" className="sm:col-span-2">
                <Input id="r-desc" value={description} disabled={readOnly} maxLength={1000} onChange={(e) => setDescription(e.target.value)} />
              </Field>
              <Field label="Method (optional)" htmlFor="r-instr" className="sm:col-span-2">
                <Textarea id="r-instr" rows={4} value={instructions} disabled={readOnly} maxLength={10000} onChange={(e) => setInstructions(e.target.value)} />
              </Field>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex-row items-center justify-between">
              <div>
                <CardTitle>Ingredients</CardTitle>
                <CardDescription>{ingredients.length} added</CardDescription>
              </div>
              {!readOnly && (
                <Button size="sm" onClick={() => setPicking(true)}>
                  <Plus /> Add ingredient
                </Button>
              )}
            </CardHeader>
            <CardContent>
              {ingredients.length === 0 ? (
                <p className="text-sm text-muted-foreground">No ingredients yet. Add raw rice, dal, vegetables, oil and so on with their amounts.</p>
              ) : (
                <ul className="divide-y">
                  {ingredients.map((i) => (
                    <li key={i.key} className="flex items-center gap-3 py-2">
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium">{i.foodName}</p>
                        <div className="flex flex-wrap items-center gap-1 text-xs text-muted-foreground">
                          {fmt(i.quantity, 2)} {i.unitLabel}
                          {i.weightState === "COOKED_WEIGHT_OF_RAW_FOOD" && " cooked weight"}
                          <DataQualityBadge quality={i.dataQuality} />
                        </div>
                      </div>
                      {!readOnly && (
                        <Button variant="ghost" size="icon-sm" aria-label={`Remove ${i.foodName}`} onClick={() => setIngredients((list) => list.filter((x) => x.key !== i.key))}>
                          <Trash2 />
                        </Button>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </div>

        <Card className="content-start lg:col-span-2">
          <CardHeader>
            <CardTitle>Nutrition per serving</CardTitle>
            <CardDescription>
              {nutrition
                ? `${fmt(nutrition.servingWeightG)} g per serving · dish ${fmt(nutrition.cookedWeightG)} g from ${fmt(nutrition.ingredientWeightG)} g of ingredients`
                : "Add ingredients to see the calculation."}
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            {preview.isFetching && !nutrition && <Skeleton className="h-40" />}
            {preview.error && <p className="text-sm text-destructive">{errorMessage(preview.error)}</p>}
            {nutrition && (
              <>
                {nutrition.warnings.map((w) => (
                  <p key={w} className="flex gap-2 rounded-md bg-warning/15 p-2 text-xs">
                    <AlertTriangle className="size-4 shrink-0" aria-hidden /> {w}
                  </p>
                ))}
                <dl className="grid grid-cols-2 gap-3">
                  {headline.map((n) => (
                    <div key={n!.code} className="rounded-lg bg-muted/60 p-3">
                      <dt className="text-xs text-muted-foreground">{n!.name}</dt>
                      <dd className="text-lg font-semibold tabular-nums">{amountWithUnit(n!.perServing, n!.unit)}</dd>
                    </div>
                  ))}
                </dl>
                {others.length > 0 && (
                  <details>
                    <summary className="cursor-pointer text-sm font-medium">All nutrients ({others.length})</summary>
                    <table className="mt-2 w-full text-sm">
                      <tbody>
                        {others.map((n) => (
                          <tr key={n.code} className="border-b last:border-0">
                            <th scope="row" className="py-1 text-left font-normal">
                              {n.name}
                            </th>
                            <td className="py-1 text-right tabular-nums">
                              {amountWithUnit(n.perServing, n.unit)}
                              {n.coveragePct !== undefined && (
                                <Badge variant="warning" className="ml-1" title="Share of ingredient weight with data for this nutrient">
                                  {fmt(n.coveragePct)}%
                                </Badge>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </details>
                )}
                {nutrition.missingNutrients.length > 0 && (
                  <p className="text-xs text-muted-foreground">
                    Not shown ({nutrition.missingNutrients.length}): less than 90% of the ingredient weight has data, so a total would be misleading.
                  </p>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </div>

      <AddFoodDialog
        open={picking}
        onOpenChange={setPicking}
        date={todayIso()}
        onPick={(food, v) => {
          const opt = v.portionId ? food.portions.find((p) => p.id === v.portionId)?.label : undefined;
          setIngredients((list) => [
            ...list,
            {
              key: `${Date.now()}-${food.id}`,
              foodId: food.id,
              foodName: food.name,
              dataQuality: food.dataQuality,
              quantity: v.quantity,
              unit: v.unit,
              unitLabel: opt ?? unitName(v.unit),
              portionId: v.portionId,
              weightState: v.weightState,
            },
          ]);
        }}
      />
      {recipe && (
        <LogRecipeDialog
          recipe={logging ? { id: recipe.id, name: recipe.name, servings: recipe.servings, servingLabel: recipe.servingLabel, servingWeightG: recipe.nutrition.servingWeightG } : null}
          onOpenChange={setLogging}
        />
      )}
      {!recipe && (
        <p className="mt-4 text-xs text-muted-foreground">
          Tip: built-in recipes like idli and sambar are in <Link className="text-primary hover:underline" to="/recipes">Recipes</Link>; duplicate one as a starting point.
        </p>
      )}
    </>
  );
}
