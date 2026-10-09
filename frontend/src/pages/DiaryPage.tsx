import { useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { CheckCircle2, Copy, MoreHorizontal, Pencil, Plus, Save, Trash2, BookmarkPlus } from "lucide-react";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { PageHeader } from "@/components/layout/PageHeader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErrorState } from "@/components/ui/empty-state";
import { Input, NativeSelect } from "@/components/ui/input";
import { Field } from "@/components/ui/label";
import { PageSkeleton } from "@/components/ui/skeleton";
import { AddFoodDialog } from "@/components/nutrition/AddFoodDialog";
import { DataQualityBadge } from "@/components/nutrition/badges";
import { DateNav } from "@/components/nutrition/DateNav";
import {
  useCopyDay,
  useCopyMeal,
  useDeleteEntry,
  useDiary,
  useSavedMealActions,
  useSavedMeals,
  useSetDayStatus,
} from "@/hooks/queries";
import { useDateParam } from "@/hooks/useDateParam";
import { errorMessage } from "@/lib/api";
import { prettyDate, shiftIso, todayIso } from "@/lib/dates";
import { MEAL_LABELS, MEAL_TYPES, type DiaryEntry, type Macros, type MealType } from "@/lib/types";
import { fmt } from "@/lib/utils";

function MacroLine({ totals }: { totals: Macros }) {
  const item = (label: string, t: Macros[keyof Macros], unit: string, d = 1) => (
    <span title={t.entries > t.entriesWithData ? `${t.entriesWithData} of ${t.entries} foods have data` : undefined}>
      {label} {fmt(t.amount, d)}
      {unit}
      {t.entries > t.entriesWithData && <span className="text-warning-foreground dark:text-warning">*</span>}
    </span>
  );
  return (
    <p className="flex flex-wrap gap-x-3 text-xs text-muted-foreground tabular-nums">
      {item("", totals.energyKcal, " kcal", 0)}
      {item("P", totals.proteinG, " g")}
      {item("C", totals.carbsG, " g")}
      {item("F", totals.fatG, " g")}
      {item("Fibre", totals.fiberG, " g")}
    </p>
  );
}

function MenuItem({ onSelect, children, destructive }: { onSelect: () => void; children: React.ReactNode; destructive?: boolean }) {
  return (
    <DropdownMenu.Item
      onSelect={onSelect}
      className={`flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none data-[highlighted]:bg-accent ${destructive ? "text-destructive" : ""}`}
    >
      {children}
    </DropdownMenu.Item>
  );
}

export default function DiaryPage() {
  const [date, setDate] = useDateParam();
  const diary = useDiary(date);
  const del = useDeleteEntry();
  const copyDay = useCopyDay();
  const copyMeal = useCopyMeal();
  const setStatus = useSetDayStatus();
  const savedMeals = useSavedMeals();
  const savedActions = useSavedMealActions();

  const [addOpen, setAddOpen] = useState(false);
  const [addMeal, setAddMeal] = useState<MealType>("BREAKFAST");
  const [editing, setEditing] = useState<DiaryEntry | null>(null);
  const [deleting, setDeleting] = useState<DiaryEntry | null>(null);
  const [copyTarget, setCopyTarget] = useState<MealType | null>(null);
  const [copyFrom, setCopyFrom] = useState({ date: shiftIso(date, -1), meal: "BREAKFAST" as MealType });
  const [saveMealOf, setSaveMealOf] = useState<MealType | null>(null);
  const [savedName, setSavedName] = useState("");
  const [logSavedFor, setLogSavedFor] = useState<MealType | null>(null);
  const [copyDayOpen, setCopyDayOpen] = useState(false);
  const [copyDayFrom, setCopyDayFrom] = useState(shiftIso(date, -1));

  if (diary.isLoading) return <PageSkeleton />;
  if (diary.error) return <ErrorState message={errorMessage(diary.error)} onRetry={() => diary.refetch()} />;
  const day = diary.data!;

  const run = async (fn: () => Promise<unknown>, success: string) => {
    try {
      await fn();
      toast.success(success);
      return true;
    } catch (err) {
      toast.error(errorMessage(err));
      return false;
    }
  };

  return (
    <>
      <PageHeader
        title="Food diary"
        description={prettyDate(date)}
        actions={
          <>
            <DateNav date={date} onChange={setDate} />
            <Button
              variant="outline"
              onClick={() => {
                setCopyDayFrom(shiftIso(date, -1));
                setCopyDayOpen(true);
              }}
            >
              <Copy /> Copy a day
            </Button>
          </>
        }
      />

      <Card className="mb-4">
        <CardContent className="flex flex-col gap-3 pt-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm font-medium">Day total · {day.entryCount} foods</p>
            <MacroLine totals={day.totals} />
            {Object.values(day.totals).some((t) => t.entries > t.entriesWithData) && (
              <p className="mt-1 text-xs text-muted-foreground">* Some foods have no data for this value, so the total is incomplete.</p>
            )}
          </div>
          <Button
            variant={day.markedComplete ? "secondary" : "outline"}
            onClick={() =>
              run(
                () => setStatus.mutateAsync({ date, completed: !day.markedComplete, note: day.dayNote }),
                day.markedComplete ? "Day marked as in progress" : "Day marked complete",
              )
            }
          >
            <CheckCircle2 className={day.markedComplete ? "text-primary" : ""} />
            {day.markedComplete ? "Logging complete" : "Mark day complete"}
          </Button>
        </CardContent>
      </Card>

      <div className="grid gap-4">
        {day.meals.map((m) => (
          <Card key={m.mealType}>
            <CardHeader className="flex-row items-start justify-between gap-2 pb-2">
              <div>
                <CardTitle>{MEAL_LABELS[m.mealType]}</CardTitle>
                <MacroLine totals={m.totals} />
              </div>
              <div className="flex shrink-0 items-center gap-1">
                <Button
                  size="sm"
                  onClick={() => {
                    setAddMeal(m.mealType);
                    setAddOpen(true);
                  }}
                >
                  <Plus /> Add
                </Button>
                <DropdownMenu.Root>
                  <DropdownMenu.Trigger asChild>
                    <Button variant="ghost" size="icon-sm" aria-label={`More actions for ${MEAL_LABELS[m.mealType]}`}>
                      <MoreHorizontal />
                    </Button>
                  </DropdownMenu.Trigger>
                  <DropdownMenu.Portal>
                    <DropdownMenu.Content align="end" className="z-50 min-w-48 rounded-lg border bg-card p-1 shadow-md">
                      <MenuItem
                        onSelect={() => {
                          setCopyFrom({ date: shiftIso(date, -1), meal: m.mealType });
                          setCopyTarget(m.mealType);
                        }}
                      >
                        <Copy className="size-4" /> Copy from another day
                      </MenuItem>
                      <MenuItem onSelect={() => setLogSavedFor(m.mealType)}>
                        <BookmarkPlus className="size-4" /> Add a saved meal
                      </MenuItem>
                      {m.entries.length > 0 && (
                        <MenuItem
                          onSelect={() => {
                            setSavedName(`${MEAL_LABELS[m.mealType]} ${prettyDate(date, "d MMM")}`);
                            setSaveMealOf(m.mealType);
                          }}
                        >
                          <Save className="size-4" /> Save as a meal
                        </MenuItem>
                      )}
                    </DropdownMenu.Content>
                  </DropdownMenu.Portal>
                </DropdownMenu.Root>
              </div>
            </CardHeader>
            <CardContent>
              {m.entries.length === 0 ? (
                <p className="text-sm text-muted-foreground">Nothing logged yet.</p>
              ) : (
                <ul className="divide-y">
                  {m.entries.map((e) => (
                    <li key={e.id} className="flex items-center gap-3 py-2">
                      <div className="min-w-0 flex-1">
                        <Link to={`/foods/${e.foodId}`} className="font-medium hover:underline">
                          {e.foodName}
                        </Link>
                        <p className="text-xs text-muted-foreground">
                          {fmt(e.quantity, 2)} {e.portionLabel ?? e.unit.toLowerCase()}
                          {e.weightState === "COOKED_WEIGHT_OF_RAW_FOOD" && " cooked"} = {fmt(e.basisAmount, 1)}{" "}
                          {e.basisUnit.toLowerCase()}
                          {e.weightState === "COOKED_WEIGHT_OF_RAW_FOOD" && " raw (estimated)"}
                          {e.notes && ` · ${e.notes}`}
                        </p>
                        <div className="mt-1 flex flex-wrap items-center gap-1">
                          <DataQualityBadge quality={e.dataQuality} />
                          {e.unknownMacros.length > 0 && (
                            <Badge variant="warning" title="These values are unknown for this food, not zero">
                              No data: {e.unknownMacros.map((u) => u.toLowerCase().replace("_", " ")).join(", ")}
                            </Badge>
                          )}
                        </div>
                      </div>
                      <div className="text-right text-sm tabular-nums">
                        <p className="font-medium">{fmt(e.energyKcal)} kcal</p>
                        <p className="text-xs text-muted-foreground">P {fmt(e.proteinG, 1)} g</p>
                      </div>
                      <div className="flex">
                        <Button variant="ghost" size="icon-sm" onClick={() => setEditing(e)} aria-label={`Edit ${e.foodName}`}>
                          <Pencil />
                        </Button>
                        <Button variant="ghost" size="icon-sm" onClick={() => setDeleting(e)} aria-label={`Remove ${e.foodName}`}>
                          <Trash2 />
                        </Button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      <AddFoodDialog open={addOpen} onOpenChange={setAddOpen} date={date} meal={addMeal} />
      <AddFoodDialog open={!!editing} onOpenChange={(o) => !o && setEditing(null)} date={date} entry={editing} />

      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        title="Remove this entry?"
        description={deleting ? `${deleting.foodName} will be removed from ${MEAL_LABELS[deleting.mealType].toLowerCase()}.` : ""}
        confirmLabel="Remove"
        pending={del.isPending}
        onConfirm={async () => {
          if (deleting && (await run(() => del.mutateAsync(deleting.id), "Entry removed"))) setDeleting(null);
        }}
      />

      {/* Copy one meal from another day */}
      <Dialog open={!!copyTarget} onOpenChange={(o) => !o && setCopyTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Copy a meal into {copyTarget ? MEAL_LABELS[copyTarget].toLowerCase() : ""}</DialogTitle>
            <DialogDescription>The foods are added to {prettyDate(date)}; nothing already logged is replaced.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="From date" htmlFor="copy-from-date">
              <Input
                id="copy-from-date"
                type="date"
                max={todayIso()}
                value={copyFrom.date}
                onChange={(e) => setCopyFrom((c) => ({ ...c, date: e.target.value }))}
              />
            </Field>
            <Field label="From meal" htmlFor="copy-from-meal">
              <NativeSelect
                id="copy-from-meal"
                value={copyFrom.meal}
                onChange={(e) => setCopyFrom((c) => ({ ...c, meal: e.target.value as MealType }))}
              >
                {MEAL_TYPES.map((mt) => (
                  <option key={mt} value={mt}>
                    {MEAL_LABELS[mt]}
                  </option>
                ))}
              </NativeSelect>
            </Field>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCopyTarget(null)}>
              Cancel
            </Button>
            <Button
              disabled={copyMeal.isPending || !copyFrom.date}
              onClick={async () => {
                try {
                  const r = await copyMeal.mutateAsync({ fromDate: copyFrom.date, fromMeal: copyFrom.meal, toDate: date, toMeal: copyTarget! });
                  toast.success(r.copiedEntries ? `Copied ${r.copiedEntries} foods` : "That meal was empty, so nothing was copied");
                  setCopyTarget(null);
                } catch (err) {
                  toast.error(errorMessage(err));
                }
              }}
            >
              Copy meal
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Copy a whole day */}
      <Dialog open={copyDayOpen} onOpenChange={setCopyDayOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Copy a whole day</DialogTitle>
            <DialogDescription>Every meal from that day is added to {prettyDate(date)}.</DialogDescription>
          </DialogHeader>
          <Field label="Copy from" htmlFor="copy-day-from">
            <Input id="copy-day-from" type="date" max={todayIso()} value={copyDayFrom} onChange={(e) => setCopyDayFrom(e.target.value)} />
          </Field>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCopyDayOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={copyDay.isPending || !copyDayFrom || copyDayFrom === date}
              onClick={async () => {
                try {
                  const r = await copyDay.mutateAsync({ fromDate: copyDayFrom, toDate: date });
                  toast.success(r.copiedEntries ? `Copied ${r.copiedEntries} foods` : "That day was empty, so nothing was copied");
                  setCopyDayOpen(false);
                } catch (err) {
                  toast.error(errorMessage(err));
                }
              }}
            >
              Copy day
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Save a meal for reuse */}
      <Dialog open={!!saveMealOf} onOpenChange={(o) => !o && setSaveMealOf(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Save as a meal</DialogTitle>
            <DialogDescription>Log the same foods again later in one step.</DialogDescription>
          </DialogHeader>
          <Field label="Name" htmlFor="saved-name">
            <Input id="saved-name" value={savedName} maxLength={150} onChange={(e) => setSavedName(e.target.value)} />
          </Field>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSaveMealOf(null)}>
              Cancel
            </Button>
            <Button
              disabled={!savedName.trim() || savedActions.fromDiary.isPending}
              onClick={async () => {
                if (await run(() => savedActions.fromDiary.mutateAsync({ name: savedName.trim(), date, mealType: saveMealOf! }), "Meal saved")) {
                  setSaveMealOf(null);
                }
              }}
            >
              Save
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Log a saved meal */}
      <Dialog open={!!logSavedFor} onOpenChange={(o) => !o && setLogSavedFor(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add a saved meal</DialogTitle>
            <DialogDescription>To {logSavedFor ? MEAL_LABELS[logSavedFor].toLowerCase() : ""} on {prettyDate(date)}.</DialogDescription>
          </DialogHeader>
          {savedMeals.data?.length ? (
            <ul className="grid gap-2">
              {savedMeals.data.map((sm) => (
                <li key={sm.id} className="flex items-center justify-between gap-3 rounded-lg border p-3">
                  <div className="min-w-0">
                    <p className="truncate font-medium">{sm.name}</p>
                    <p className="truncate text-xs text-muted-foreground">
                      {sm.items.map((i) => i.foodName).join(", ")} · {fmt(sm.energyKcal)} kcal
                    </p>
                  </div>
                  <Button
                    size="sm"
                    disabled={savedActions.log.isPending}
                    onClick={async () => {
                      if (await run(() => savedActions.log.mutateAsync({ id: sm.id, date, mealType: logSavedFor! }), `Added ${sm.name}`)) {
                        setLogSavedFor(null);
                      }
                    }}
                  >
                    Add
                  </Button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted-foreground">No saved meals yet. Use "Save as a meal" on any meal to create one.</p>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
