import { useState } from "react";
import { Link } from "react-router-dom";
import { addMonths, eachDayOfInterval, endOfMonth, endOfWeek, format, isSameMonth, parseISO, startOfMonth, startOfWeek } from "date-fns";
import { CheckCircle2, ChevronLeft, ChevronRight, Copy } from "lucide-react";
import { toast } from "sonner";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/label";
import { PageSkeleton, Skeleton } from "@/components/ui/skeleton";
import { useCalendar, useCopyDay, useDiary } from "@/hooks/queries";
import { errorMessage } from "@/lib/api";
import { prettyDate, shiftIso, todayIso } from "@/lib/dates";
import { MEAL_LABELS, type DiaryDay, type Macros } from "@/lib/types";
import { cn, fmt } from "@/lib/utils";

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/** A day with no entries shows dashes: nothing logged is unknown intake, not zero. */
function MacroTable({ a, b, labelA, labelB }: { a?: Macros; b?: Macros | null; labelA: string; labelB?: string }) {
  const rows: { key: keyof Macros; label: string; unit: string; d: number }[] = [
    { key: "energyKcal", label: "Calories", unit: "kcal", d: 0 },
    { key: "proteinG", label: "Protein", unit: "g", d: 1 },
    { key: "carbsG", label: "Carbohydrate", unit: "g", d: 1 },
    { key: "fatG", label: "Fat", unit: "g", d: 1 },
    { key: "fiberG", label: "Fibre", unit: "g", d: 1 },
  ];
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-xs text-muted-foreground">
          <th className="py-1 text-left font-medium" />
          <th className="py-1 text-right font-medium">{labelA}</th>
          {b !== undefined && <th className="py-1 text-right font-medium">{labelB}</th>}
          {b !== undefined && <th className="py-1 text-right font-medium">Difference</th>}
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => {
          const va = a?.[r.key].amount;
          const vb = b ? b[r.key].amount : undefined;
          const diff = va !== undefined && vb !== undefined ? vb - va : undefined;
          return (
            <tr key={r.key} className="border-t">
              <th scope="row" className="py-1.5 text-left font-normal">
                {r.label}
              </th>
              <td className="py-1.5 text-right tabular-nums">
                {va === undefined ? "—" : `${fmt(va, r.d)} ${r.unit}`}
              </td>
              {b !== undefined && (
                <td className="py-1.5 text-right tabular-nums">
                  {vb === undefined ? "—" : `${fmt(vb, r.d)} ${r.unit}`}
                </td>
              )}
              {b !== undefined && (
                <td className="py-1.5 text-right tabular-nums text-muted-foreground">
                  {diff === undefined ? "—" : `${diff > 0 ? "+" : ""}${fmt(diff, r.d)}`}
                </td>
              )}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function DayPanel({ day }: { day: DiaryDay }) {
  return (
    <div className="grid gap-3">
      {day.meals
        .filter((m) => m.entries.length)
        .map((m) => (
          <div key={m.mealType}>
            <p className="text-sm font-medium">
              {MEAL_LABELS[m.mealType]} <span className="font-normal text-muted-foreground">· {fmt(m.totals.energyKcal.amount)} kcal</span>
            </p>
            <ul className="text-sm text-muted-foreground">
              {m.entries.map((e) => (
                <li key={e.id} className="truncate">
                  {e.foodName} · {fmt(e.quantity, 2)} {e.portionLabel ?? e.unit.toLowerCase()}
                </li>
              ))}
            </ul>
          </div>
        ))}
      {day.entryCount === 0 && <p className="text-sm text-muted-foreground">Nothing logged.</p>}
    </div>
  );
}

export default function CalendarPage() {
  const [month, setMonth] = useState(todayIso().slice(0, 7));
  const [selected, setSelected] = useState(todayIso());
  const [compareTo, setCompareTo] = useState("");
  const calendar = useCalendar(month);
  const day = useDiary(selected);
  const other = useDiary(compareTo || selected);
  const copyDay = useCopyDay();

  if (calendar.isLoading) return <PageSkeleton />;
  if (calendar.error) return <ErrorState message={errorMessage(calendar.error)} onRetry={() => calendar.refetch()} />;

  const first = parseISO(`${month}-01`);
  const cells = eachDayOfInterval({ start: startOfWeek(startOfMonth(first), { weekStartsOn: 1 }), end: endOfWeek(endOfMonth(first), { weekStartsOn: 1 }) });
  const byDate = new Map(calendar.data!.days.map((d) => [d.date, d]));
  const target = calendar.data!.calorieTargetKcal;
  const today = todayIso();
  const nextMonth = format(addMonths(first, 1), "yyyy-MM");

  return (
    <>
      <PageHeader title="Calendar & history" description="Pick a day to review it, compare it with another, or copy its meals." />
      <div className="grid gap-4 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle>{format(first, "MMMM yyyy")}</CardTitle>
            <div className="flex gap-1">
              <Button variant="outline" size="icon-sm" aria-label="Previous month" onClick={() => setMonth(format(addMonths(first, -1), "yyyy-MM"))}>
                <ChevronLeft />
              </Button>
              <Button variant="outline" size="icon-sm" aria-label="Next month" disabled={nextMonth > today.slice(0, 7)} onClick={() => setMonth(nextMonth)}>
                <ChevronRight />
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-7 gap-1 text-center text-xs text-muted-foreground" aria-hidden>
              {WEEKDAYS.map((w) => (
                <div key={w} className="py-1">
                  {w}
                </div>
              ))}
            </div>
            <div role="grid" aria-label={format(first, "MMMM yyyy")} className="grid grid-cols-7 gap-1">
              {cells.map((c) => {
                const iso = format(c, "yyyy-MM-dd");
                const info = byDate.get(iso);
                const inMonth = isSameMonth(c, first);
                const future = iso > today;
                const logged = !!info && info.entries > 0;
                return (
                  <button
                    key={iso}
                    type="button"
                    role="gridcell"
                    disabled={future || !inMonth}
                    aria-selected={selected === iso}
                    aria-label={`${prettyDate(iso)}${logged ? `, ${info!.entries} foods, ${fmt(info!.energyKcal)} kcal` : ", nothing logged"}${info?.completed ? ", complete" : ""}`}
                    onClick={() => setSelected(iso)}
                    className={cn(
                      "flex aspect-square flex-col items-center justify-start gap-0.5 rounded-lg border p-1 text-xs transition-colors sm:aspect-auto sm:min-h-16",
                      !inMonth && "invisible",
                      future && "opacity-40",
                      logged && info?.completed && "border-primary/50 bg-primary/10",
                      logged && !info?.completed && "border-warning/60 bg-warning/10",
                      selected === iso && "ring-2 ring-ring",
                      iso === today && "font-bold",
                    )}
                  >
                    <span>{format(c, "d")}</span>
                    {logged && <span className="hidden tabular-nums text-muted-foreground sm:block">{fmt(info!.energyKcal)}</span>}
                    {info?.completed && <CheckCircle2 className="size-3 text-primary" aria-hidden />}
                  </button>
                );
              })}
            </div>
            <div className="mt-3 flex flex-wrap gap-3 text-xs text-muted-foreground">
              <span className="flex items-center gap-1">
                <span className="size-3 rounded border border-primary/50 bg-primary/10" /> Marked complete
              </span>
              <span className="flex items-center gap-1">
                <span className="size-3 rounded border border-warning/60 bg-warning/10" /> Logged, not marked complete
              </span>
              {target && <span>Numbers are kcal (target {fmt(target)})</span>}
            </div>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>{prettyDate(selected)}</CardTitle>
            <CardDescription>
              {day.data ? `${day.data.entryCount} foods${day.data.markedComplete ? " · marked complete" : day.data.entryCount ? " · not marked complete" : ""}` : ""}
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            {day.isLoading || !day.data ? (
              <Skeleton className="h-40" />
            ) : (
              <>
                <MacroTable
                  a={day.data.entryCount ? day.data.totals : undefined}
                  b={compareTo && other.data && compareTo !== selected ? (other.data.entryCount ? other.data.totals : null) : undefined}
                  labelA={prettyDate(selected, "d MMM")}
                  labelB={compareTo ? prettyDate(compareTo, "d MMM") : undefined}
                />
                <DayPanel day={day.data} />
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="outline" asChild>
                    <Link to={`/diary?date=${selected}`}>Open in diary</Link>
                  </Button>
                  {selected !== today && day.data.entryCount > 0 && (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={copyDay.isPending}
                      onClick={async () => {
                        try {
                          const r = await copyDay.mutateAsync({ fromDate: selected, toDate: today });
                          toast.success(`Copied ${r.copiedEntries} foods to today`);
                        } catch (err) {
                          toast.error(errorMessage(err));
                        }
                      }}
                    >
                      <Copy /> Copy to today
                    </Button>
                  )}
                </div>
                <Field label="Compare with" htmlFor="compare-date">
                  <Input id="compare-date" type="date" max={today} value={compareTo} onChange={(e) => setCompareTo(e.target.value)} />
                </Field>
                {!compareTo && (
                  <Button size="sm" variant="ghost" onClick={() => setCompareTo(shiftIso(selected, -1))}>
                    Compare with the day before
                  </Button>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </>
  );
}
