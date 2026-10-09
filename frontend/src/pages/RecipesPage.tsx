import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { ChefHat, Copy, Plus, Search, Trash2, UtensilsCrossed } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { EmptyState, ErrorState } from "@/components/ui/empty-state";
import { Input, NativeSelect } from "@/components/ui/input";
import { Field } from "@/components/ui/label";
import { PageSkeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { LogRecipeDialog } from "@/components/nutrition/LogRecipeDialog";
import { useRecipeAction, useRecipes, useSavedMealActions, useSavedMeals } from "@/hooks/queries";
import { errorMessage } from "@/lib/api";
import { todayIso } from "@/lib/dates";
import { MEAL_LABELS, MEAL_TYPES, type MealType, type RecipeSummary, type SavedMeal } from "@/lib/types";
import { fmt } from "@/lib/utils";

function RecipeList() {
  const recipes = useRecipes();
  const { duplicate, remove } = useRecipeAction();
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<"all" | "mine" | "system">("all");
  const [logging, setLogging] = useState<RecipeSummary | null>(null);
  const [deleting, setDeleting] = useState<RecipeSummary | null>(null);

  if (recipes.isLoading) return <PageSkeleton />;
  if (recipes.error) return <ErrorState message={errorMessage(recipes.error)} onRetry={() => recipes.refetch()} />;
  const list = (recipes.data ?? [])
    .filter((r) => (filter === "mine" ? r.owned : filter === "system" ? r.system : true))
    .filter((r) => r.name.toLowerCase().includes(q.trim().toLowerCase()));

  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input aria-label="Search recipes" placeholder="Search recipes" className="pl-9" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <NativeSelect aria-label="Show" value={filter} onChange={(e) => setFilter(e.target.value as typeof filter)} className="sm:w-44">
          <option value="all">All recipes</option>
          <option value="mine">My recipes</option>
          <option value="system">Built-in recipes</option>
        </NativeSelect>
      </div>
      {list.length === 0 ? (
        <EmptyState
          icon={ChefHat}
          title="No recipes here"
          description="Build a recipe from ingredients to see its nutrition per serving."
          action={
            <Button asChild>
              <Link to="/recipes/new">
                <Plus /> New recipe
              </Link>
            </Button>
          }
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((r) => (
            <Card key={r.id} className="flex flex-col">
              <CardHeader>
                <div className="flex items-start justify-between gap-2">
                  <CardTitle>
                    <Link to={`/recipes/${r.id}`} className="hover:underline">
                      {r.name}
                    </Link>
                  </CardTitle>
                  <Badge variant={r.system ? "secondary" : "default"}>{r.system ? "Built-in" : "Mine"}</Badge>
                </div>
                {r.description && <CardDescription className="line-clamp-2">{r.description}</CardDescription>}
              </CardHeader>
              <CardContent className="flex-1 text-sm">
                <p className="tabular-nums">
                  <span className="text-lg font-semibold">{fmt(r.energyKcalPerServing)}</span> kcal ·{" "}
                  {fmt(r.proteinGPerServing, 1)} g protein
                </p>
                <p className="text-muted-foreground">
                  per {r.servingLabel ?? "serving"}
                  {r.servingWeightG ? ` (${fmt(r.servingWeightG)} g)` : ""} · {r.ingredientCount} ingredients
                </p>
              </CardContent>
              <CardFooter className="flex-wrap gap-2">
                <Button size="sm" onClick={() => setLogging(r)}>
                  Log
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={duplicate.isPending}
                  onClick={async () => {
                    try {
                      const copy = await duplicate.mutateAsync(r.id);
                      toast.success("Copied to your recipes");
                      navigate(`/recipes/${copy.id}`);
                    } catch (err) {
                      toast.error(errorMessage(err));
                    }
                  }}
                >
                  <Copy /> Duplicate
                </Button>
                {r.owned && (
                  <Button size="sm" variant="ghost" onClick={() => setDeleting(r)} aria-label={`Delete ${r.name}`}>
                    <Trash2 />
                  </Button>
                )}
              </CardFooter>
            </Card>
          ))}
        </div>
      )}
      <LogRecipeDialog recipe={logging} onOpenChange={(o) => !o && setLogging(null)} />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        title="Delete this recipe?"
        description="It won't appear in search any more. Diary entries that already use it are kept."
        pending={remove.isPending}
        onConfirm={async () => {
          try {
            await remove.mutateAsync(deleting!.id);
            toast.success("Recipe deleted");
            setDeleting(null);
          } catch (err) {
            toast.error(errorMessage(err));
          }
        }}
      />
    </div>
  );
}

function SavedMealList() {
  const meals = useSavedMeals();
  const actions = useSavedMealActions();
  const [logging, setLogging] = useState<SavedMeal | null>(null);
  const [deleting, setDeleting] = useState<SavedMeal | null>(null);
  const [date, setDate] = useState(todayIso());
  const [meal, setMeal] = useState<MealType>("BREAKFAST");

  if (meals.isLoading) return <PageSkeleton />;
  if (meals.error) return <ErrorState message={errorMessage(meals.error)} onRetry={() => meals.refetch()} />;
  if (!meals.data?.length)
    return (
      <EmptyState
        icon={UtensilsCrossed}
        title="No saved meals yet"
        description='In the food diary, open a meal&apos;s menu and choose "Save as a meal" to reuse it.'
        action={
          <Button asChild variant="outline">
            <Link to="/diary">Open the diary</Link>
          </Button>
        }
      />
    );

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {meals.data.map((m) => (
        <Card key={m.id} className="flex flex-col">
          <CardHeader>
            <CardTitle>{m.name}</CardTitle>
            <CardDescription className="tabular-nums">
              {fmt(m.energyKcal)} kcal · P {fmt(m.proteinG, 1)} g · C {fmt(m.carbsG, 1)} g · F {fmt(m.fatG, 1)} g
            </CardDescription>
          </CardHeader>
          <CardContent className="flex-1">
            <ul className="text-sm text-muted-foreground">
              {m.items.map((i) => (
                <li key={i.id}>
                  {i.foodName} · {fmt(i.quantity, 2)} {i.unit.toLowerCase()}
                </li>
              ))}
            </ul>
          </CardContent>
          <CardFooter className="gap-2">
            <Button
              size="sm"
              onClick={() => {
                setMeal(m.mealType ?? "BREAKFAST");
                setLogging(m);
              }}
            >
              Log
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setDeleting(m)} aria-label={`Delete ${m.name}`}>
              <Trash2 />
            </Button>
          </CardFooter>
        </Card>
      ))}
      <Dialog open={!!logging} onOpenChange={(o) => !o && setLogging(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Log {logging?.name}</DialogTitle>
            <DialogDescription>All its foods are added to the diary.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Date" htmlFor="sm-date">
              <Input id="sm-date" type="date" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} />
            </Field>
            <Field label="Meal" htmlFor="sm-meal">
              <NativeSelect id="sm-meal" value={meal} onChange={(e) => setMeal(e.target.value as MealType)}>
                {MEAL_TYPES.map((mt) => (
                  <option key={mt} value={mt}>
                    {MEAL_LABELS[mt]}
                  </option>
                ))}
              </NativeSelect>
            </Field>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setLogging(null)}>
              Cancel
            </Button>
            <Button
              disabled={actions.log.isPending}
              onClick={async () => {
                try {
                  const r = await actions.log.mutateAsync({ id: logging!.id, date, mealType: meal });
                  toast.success(`Added ${r.loggedEntries} foods`);
                  setLogging(null);
                } catch (err) {
                  toast.error(errorMessage(err));
                }
              }}
            >
              Log meal
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        title="Delete this saved meal?"
        description="Diary entries already logged from it are kept."
        pending={actions.remove.isPending}
        onConfirm={async () => {
          try {
            await actions.remove.mutateAsync(deleting!.id);
            toast.success("Saved meal deleted");
            setDeleting(null);
          } catch (err) {
            toast.error(errorMessage(err));
          }
        }}
      />
    </div>
  );
}

export default function RecipesPage() {
  return (
    <>
      <PageHeader
        title="Recipes & saved meals"
        description="Recipes are worked out per serving from their ingredients and cooked weight."
        actions={
          <Button asChild>
            <Link to="/recipes/new">
              <Plus /> New recipe
            </Link>
          </Button>
        }
      />
      <Tabs defaultValue="recipes">
        <TabsList>
          <TabsTrigger value="recipes">Recipes</TabsTrigger>
          <TabsTrigger value="saved">Saved meals</TabsTrigger>
        </TabsList>
        <TabsContent value="recipes">
          <RecipeList />
        </TabsContent>
        <TabsContent value="saved">
          <SavedMealList />
        </TabsContent>
      </Tabs>
    </>
  );
}
