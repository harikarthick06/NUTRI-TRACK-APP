import { useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { ChefHat, ExternalLink, Pencil, Plus, Star, Trash2 } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ErrorState } from "@/components/ui/empty-state";
import { NativeSelect } from "@/components/ui/input";
import { PageSkeleton } from "@/components/ui/skeleton";
import { AddFoodDialog } from "@/components/nutrition/AddFoodDialog";
import { CompletenessBadge, DataQualityBadge } from "@/components/nutrition/badges";
import { useArchiveFood, useFood, useToggleFavorite } from "@/hooks/queries";
import { errorMessage } from "@/lib/api";
import { todayIso } from "@/lib/dates";
import type { FoodNutrient } from "@/lib/types";
import { amountWithUnit, fmt, titleCase } from "@/lib/utils";

const CATEGORY_ORDER = ["ENERGY", "MACRONUTRIENT", "FATTY_ACID", "VITAMIN", "MINERAL", "OTHER"];
const CATEGORY_LABELS: Record<string, string> = {
  ENERGY: "Energy",
  MACRONUTRIENT: "Macronutrients",
  FATTY_ACID: "Fats and fatty acids",
  VITAMIN: "Vitamins",
  MINERAL: "Minerals",
  OTHER: "Other",
};
const ORIGIN_LABELS: Record<string, string> = {
  REPORTED: "",
  DERIVED: "derived",
  CALCULATED: "calculated",
  USER_ENTERED: "user entered",
};

export default function FoodDetailPage() {
  const { id } = useParams();
  const foodId = Number(id);
  const food = useFood(Number.isFinite(foodId) ? foodId : null);
  const fav = useToggleFavorite();
  const archive = useArchiveFood();
  const navigate = useNavigate();
  const [addOpen, setAddOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [amountKey, setAmountKey] = useState("basis");

  const f = food.data;
  const amountOptions = useMemo(() => {
    if (!f) return [];
    const unit = f.basisUnit === "G" ? "g" : "mL";
    return [
      { key: "basis", label: `Per ${fmt(f.basisQuantity)} ${unit}`, factor: 1 },
      ...f.portions.map((p) => ({ key: `p${p.id}`, label: `${p.label} (${fmt(p.basisAmount, 1)} ${unit})`, factor: p.basisAmount / f.basisQuantity })),
    ];
  }, [f]);

  if (food.isLoading) return <PageSkeleton />;
  if (food.error || !f) return <ErrorState message={errorMessage(food.error)} onRetry={() => food.refetch()} />;

  const factor = amountOptions.find((o) => o.key === amountKey)?.factor ?? 1;
  const groups = CATEGORY_ORDER.map((c) => ({ category: c, rows: f.nutrients.filter((n) => n.category === c) })).filter((g) => g.rows.length);
  const knownCount = f.nutrients.filter((n) => n.known).length;
  const canEdit = f.owned && f.foodType === "CUSTOM";

  const row = (n: FoodNutrient) => (
    <tr key={n.code} className="border-b last:border-0">
      <th scope="row" className="py-2 pr-2 text-left font-normal">
        {n.name}
        {n.formNote && <span className="block text-xs text-muted-foreground">{n.formNote}</span>}
      </th>
      <td className="py-2 text-right tabular-nums">
        {n.known && n.amount !== undefined ? (
          <>
            {amountWithUnit(n.amount * factor, n.unit)}
            {n.valueOrigin && ORIGIN_LABELS[n.valueOrigin] && (
              <span className="ml-1 text-xs text-muted-foreground">({ORIGIN_LABELS[n.valueOrigin]})</span>
            )}
            {n.coveragePct !== undefined && n.coveragePct < 100 && (
              <span className="ml-1 text-xs text-warning-foreground dark:text-warning" title="Share of the recipe's ingredients with data">
                {fmt(n.coveragePct)}% of ingredients
              </span>
            )}
          </>
        ) : (
          <span className="text-muted-foreground" title="This value is unknown for this food. It is not counted as zero.">
            No data
          </span>
        )}
      </td>
    </tr>
  );

  return (
    <>
      <PageHeader
        title={f.name}
        description={[f.brand, f.category, f.localNames].filter(Boolean).join(" · ")}
        actions={
          <>
            <Button
              variant="outline"
              aria-pressed={f.favorite}
              onClick={() => fav.mutate({ id: f.id, favorite: !f.favorite }, { onError: (e) => toast.error(errorMessage(e)) })}
            >
              <Star className={f.favorite ? "fill-current text-carbs" : ""} /> {f.favorite ? "Favourite" : "Add to favourites"}
            </Button>
            {f.recipeId && (
              <Button variant="outline" asChild>
                <Link to={`/recipes/${f.recipeId}`}>
                  <ChefHat /> Recipe
                </Link>
              </Button>
            )}
            {canEdit && (
              <>
                <Button variant="outline" asChild>
                  <Link to={`/foods/${f.id}/edit`}>
                    <Pencil /> Edit
                  </Link>
                </Button>
                <Button variant="outline" onClick={() => setConfirmDelete(true)}>
                  <Trash2 /> Delete
                </Button>
              </>
            )}
            <Button onClick={() => setAddOpen(true)}>
              <Plus /> Add to diary
            </Button>
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="grid content-start gap-4">
          <Card>
            <CardHeader>
              <CardTitle>About this food</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm">
              <div className="flex flex-wrap gap-1">
                <DataQualityBadge quality={f.dataQuality} />
                <CompletenessBadge pct={f.completenessPct} />
                <Badge variant="outline">{titleCase(f.foodType)}</Badge>
                {f.preparationState !== "NOT_APPLICABLE" && <Badge variant="outline">{titleCase(f.preparationState)}</Badge>}
              </div>
              <p className="text-muted-foreground">
                {knownCount} of {f.nutrients.length} tracked nutrients have data.
              </p>
              {f.source && (
                <div>
                  <p className="font-medium">Source</p>
                  <p className="text-muted-foreground">
                    {f.source.name}
                    {f.source.version && ` (${f.source.version})`}
                    {f.sourceFoodRef && ` · code ${f.sourceFoodRef}`}
                  </p>
                  {f.source.url && (
                    <a href={f.source.url} target="_blank" rel="noreferrer noopener" className="inline-flex items-center gap-1 text-primary hover:underline">
                      Visit source <ExternalLink className="size-3" />
                    </a>
                  )}
                  {f.source.licenseNote && <p className="mt-1 text-xs text-muted-foreground">{f.source.licenseNote}</p>}
                </div>
              )}
              {f.cookedYieldFactor && (
                <div>
                  <p className="font-medium">Raw to cooked</p>
                  <p className="text-muted-foreground">
                    100 g {f.preparationState.toLowerCase()} becomes about {fmt(f.cookedYieldFactor * 100)} g cooked.{" "}
                    {f.cookedYieldNote}
                  </p>
                </div>
              )}
              {f.notes && <p className="text-muted-foreground">{f.notes}</p>}
              {f.barcode && <p className="text-muted-foreground">Barcode {f.barcode}</p>}
            </CardContent>
          </Card>

          {f.energyCheck && f.energyCheck.status !== "INSUFFICIENT_DATA" && (
            <Card>
              <CardHeader>
                <CardTitle>Energy check</CardTitle>
                <CardDescription>Reported calories compared with an estimate from protein, carbs, fat and fibre</CardDescription>
              </CardHeader>
              <CardContent className="text-sm">
                <p>
                  Reported {fmt(f.energyCheck.reportedKcal)} kcal · estimated {fmt(f.energyCheck.estimatedKcal)} kcal
                  {f.energyCheck.differencePct !== undefined && ` (${fmt(f.energyCheck.differencePct, 0)}% difference)`}
                </p>
                {f.energyCheck.note && <p className="mt-1 text-muted-foreground">{f.energyCheck.note}</p>}
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader>
              <CardTitle>Portions</CardTitle>
            </CardHeader>
            <CardContent>
              {f.portions.length === 0 ? (
                <p className="text-sm text-muted-foreground">Log this food by weight{f.basisUnit === "ML" ? " or volume" : ""}.</p>
              ) : (
                <ul className="grid gap-1 text-sm">
                  {f.portions.map((p) => (
                    <li key={p.id} className="flex justify-between gap-2">
                      <span>
                        {p.label} {p.isDefault && <Badge variant="secondary">default</Badge>}
                      </span>
                      <span className="tabular-nums text-muted-foreground">
                        {fmt(p.basisAmount, 1)} {f.basisUnit === "G" ? "g" : "mL"}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </div>

        <Card className="lg:col-span-2">
          <CardHeader className="flex-row flex-wrap items-center justify-between gap-2">
            <CardTitle>Nutrition</CardTitle>
            <NativeSelect aria-label="Amount" value={amountKey} onChange={(e) => setAmountKey(e.target.value)} className="w-auto">
              {amountOptions.map((o) => (
                <option key={o.key} value={o.key}>
                  {o.label}
                </option>
              ))}
            </NativeSelect>
          </CardHeader>
          <CardContent className="grid gap-6">
            {groups.map((g) => (
              <section key={g.category}>
                <h3 className="mb-1 text-sm font-semibold">{CATEGORY_LABELS[g.category] ?? g.category}</h3>
                <table className="w-full text-sm">
                  <tbody>{g.rows.map(row)}</tbody>
                </table>
              </section>
            ))}
            <p className="text-xs text-muted-foreground">"No data" means the source didn't measure it. It is left out of totals, not counted as zero.</p>
          </CardContent>
        </Card>
      </div>

      <AddFoodDialog open={addOpen} onOpenChange={setAddOpen} date={todayIso()} initialFoodId={f.id} />
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete this food?"
        description="It will no longer appear in search. Diary entries that already use it are kept."
        pending={archive.isPending}
        onConfirm={async () => {
          try {
            await archive.mutateAsync(f.id);
            toast.success("Food deleted");
            navigate("/foods", { replace: true });
          } catch (err) {
            toast.error(errorMessage(err));
          }
        }}
      />
    </>
  );
}
