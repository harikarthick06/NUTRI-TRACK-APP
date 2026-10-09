import { useMemo, useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { format, parseISO, addMonths, startOfWeek, addWeeks } from "date-fns";
import { ChevronLeft, ChevronRight, Download } from "lucide-react";
import { toast } from "sonner";
import { PageHeader } from "@/components/layout/PageHeader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState, ErrorState } from "@/components/ui/empty-state";
import { Input, NativeSelect } from "@/components/ui/input";
import { Field } from "@/components/ui/label";
import { PageSkeleton, Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CoverageBadge } from "@/components/nutrition/badges";
import { typeLabel } from "@/components/nutrition/NutrientTable";
import { useDailyNutrition, useNutrients, useReport, useTrend, type ReportParams } from "@/hooks/queries";
import { download, errorMessage } from "@/lib/api";
import { prettyDate, shiftIso, todayIso } from "@/lib/dates";
import { MEAL_LABELS, type RangeReport } from "@/lib/types";
import { amountWithUnit, fmt } from "@/lib/utils";

type Period = "day" | "week" | "month" | "custom";
const tooltipStyle = { background: "var(--card)", border: "1px solid var(--border)", borderRadius: 8 };
const axisTick = { fontSize: 11, fill: "var(--muted-foreground)" };

async function exportCsv(path: string, query: Record<string, string>, name: string) {
  try {
    await download(path, query, name);
  } catch (err) {
    toast.error(errorMessage(err));
  }
}

function Stat({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <Card>
      <CardContent className="pt-5">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="text-2xl font-semibold tabular-nums">{value}</p>
        {sub && <p className="text-xs text-muted-foreground">{sub}</p>}
      </CardContent>
    </Card>
  );
}

function RangeView({ report }: { report: RangeReport }) {
  const avg = (code: string) => report.nutrients.find((n) => n.code === code);
  const kcal = avg("ENERGY_KCAL");
  const daily = report.dailyRows.map((d) => ({ label: format(parseISO(d.date), report.days > 14 ? "d" : "EEE d"), kcal: d.energyKcal ?? null, logged: d.entries > 0 }));
  const split = report.macroSplit
    ? [
        { name: "Protein", value: report.macroSplit.proteinPct, color: "var(--protein)" },
        { name: "Carbs", value: report.macroSplit.carbsPct, color: "var(--carbs)" },
        { name: "Fat", value: report.macroSplit.fatPct, color: "var(--fat)" },
      ]
    : null;
  const meals = report.meals.map((m) => ({ name: MEAL_LABELS[m.mealType], kcal: m.energyKcal ?? 0 }));
  const micros = report.nutrients.filter((n) => !["ENERGY"].includes(n.category));

  if (report.loggedDays === 0) {
    return <EmptyState title="Nothing logged in this period" description="Reports appear once you've logged food on at least one day." />;
  }

  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Average calories" value={`${fmt(kcal?.average)} kcal`} sub={`per logged day · ${report.loggedDays} of ${report.days} days logged`} />
        <Stat
          label="Average protein · carbs · fat"
          value={`${fmt(avg("PROTEIN")?.average)} · ${fmt(avg("CARBOHYDRATE")?.average)} · ${fmt(avg("FAT_TOTAL")?.average)} g`}
          sub={`Fibre ${fmt(avg("FIBER")?.average, 1)} g`}
        />
        <Stat
          label="Days near calorie target"
          value={report.calorieTargetKcal ? `${report.daysNearCalorieTarget} of ${report.loggedDays}` : "—"}
          sub={report.calorieTargetKcal ? `within 10% of ${fmt(report.calorieTargetKcal)} kcal` : "set a calorie target"}
        />
        <Stat
          label="Weight change"
          value={report.weight ? `${report.weight.changeKg > 0 ? "+" : ""}${fmt(report.weight.changeKg, 1)} kg` : "—"}
          sub={report.weight ? `${fmt(report.weight.startKg, 1)} → ${fmt(report.weight.endKg, 1)} kg` : "no weigh-ins"}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Daily calories</CardTitle>
            <CardDescription>Days with nothing logged are left blank</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-64" role="img" aria-label="Daily calories chart">
              <ResponsiveContainer>
                <BarChart data={daily} margin={{ left: 0, right: 8, top: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                  <XAxis dataKey="label" tick={axisTick} interval="preserveStartEnd" />
                  <YAxis tick={axisTick} width={44} />
                  <Tooltip contentStyle={tooltipStyle} formatter={(raw) => { const v = Number(raw); return `${fmt(v)} kcal`; }} />
                  {report.calorieTargetKcal && <ReferenceLine y={report.calorieTargetKcal} stroke="var(--primary)" strokeDasharray="4 4" />}
                  <Bar dataKey="kcal" name="Calories" fill="var(--carbs)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Macro split</CardTitle>
            <CardDescription>Share of calories, averaged</CardDescription>
          </CardHeader>
          <CardContent>
            {split ? (
              <>
                <div className="h-40">
                  <ResponsiveContainer>
                    <PieChart>
                      <Pie data={split} dataKey="value" innerRadius={40} outerRadius={64} paddingAngle={2} stroke="none">
                        {split.map((s) => (
                          <Cell key={s.name} fill={s.color} />
                        ))}
                      </Pie>
                      <Tooltip contentStyle={tooltipStyle} formatter={(raw) => { const v = Number(raw); return `${fmt(v, 1)}%`; }} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <ul className="mt-2 grid gap-1 text-sm">
                  {split.map((s) => (
                    <li key={s.name} className="flex items-center gap-2">
                      <span className="size-3 rounded-full" style={{ background: s.color }} aria-hidden /> {s.name} {fmt(s.value, 1)}%
                    </li>
                  ))}
                </ul>
              </>
            ) : (
              <p className="text-sm text-muted-foreground">Some days are missing macro data, so a split would be misleading.</p>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Calories by meal</CardTitle>
            <CardDescription>Total over the period</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-56" role="img" aria-label="Calories by meal chart">
              <ResponsiveContainer>
                <BarChart data={meals} layout="vertical" margin={{ left: 8, right: 8 }}>
                  <XAxis type="number" tick={axisTick} />
                  <YAxis type="category" dataKey="name" tick={axisTick} width={96} />
                  <Tooltip contentStyle={tooltipStyle} formatter={(raw) => { const v = Number(raw); return `${fmt(v)} kcal`; }} />
                  <Bar dataKey="kcal" name="Calories" fill="var(--primary)" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Top foods</CardTitle>
            <CardDescription>By calories over the period</CardDescription>
          </CardHeader>
          <CardContent>
            <ol className="grid gap-1 text-sm">
              {report.topFoods.map((f, i) => (
                <li key={f.foodId} className="flex justify-between gap-2">
                  <span className="truncate">
                    {i + 1}. {f.name} <span className="text-muted-foreground">· {f.timesLogged}×</span>
                  </span>
                  <span className="tabular-nums text-muted-foreground">{fmt(f.energyKcal)} kcal</span>
                </li>
              ))}
            </ol>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Nutrient averages and targets</CardTitle>
          <CardDescription>Averaged over logged days that had data for each nutrient</CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="border-b text-left text-xs text-muted-foreground">
                <th className="py-2 font-medium">Nutrient</th>
                <th className="py-2 text-right font-medium">Average / day</th>
                <th className="py-2 text-right font-medium">Target</th>
                <th className="py-2 text-right font-medium">% of target</th>
                <th className="py-2 text-right font-medium">Days with data</th>
                <th className="py-2 text-right font-medium">Days above limit</th>
              </tr>
            </thead>
            <tbody>
              {micros.map((n) => (
                <tr key={n.code} className="border-b last:border-0">
                  <th scope="row" className="py-1.5 text-left font-normal">
                    {n.name}
                  </th>
                  <td className="py-1.5 text-right tabular-nums">{n.average === undefined ? "—" : amountWithUnit(n.average, n.unit)}</td>
                  <td className="py-1.5 text-right tabular-nums text-muted-foreground">
                    {n.target === undefined ? "—" : `${amountWithUnit(n.target, n.unit)} ${typeLabel(n.targetType)}`}
                  </td>
                  <td className="py-1.5 text-right tabular-nums">{n.percentOfTarget === undefined ? "—" : `${fmt(n.percentOfTarget)}%`}</td>
                  <td className="py-1.5 text-right">
                    <Badge variant={n.daysWithData < n.loggedDays ? "warning" : "secondary"}>
                      {n.daysWithData}/{n.loggedDays}
                    </Badge>
                  </td>
                  <td className="py-1.5 text-right tabular-nums">
                    {n.upperLimitComparable ? (n.daysAboveUpperLimit ? <span className="text-destructive">{n.daysAboveUpperLimit}</span> : 0) : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}

function DayView({ date }: { date: string }) {
  const daily = useDailyNutrition(date);
  if (daily.isLoading) return <Skeleton className="h-64" />;
  if (daily.error) return <ErrorState message={errorMessage(daily.error)} onRetry={() => daily.refetch()} />;
  const d = daily.data!;
  if (d.entryCount === 0) return <EmptyState title="Nothing logged on this day" />;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{prettyDate(date)}</CardTitle>
        <CardDescription>{d.entryCount} foods logged</CardDescription>
      </CardHeader>
      <CardContent className="overflow-x-auto">
        <table className="w-full min-w-[600px] text-sm">
          <thead>
            <tr className="border-b text-left text-xs text-muted-foreground">
              <th className="py-2 font-medium">Nutrient</th>
              <th className="py-2 text-right font-medium">Amount</th>
              <th className="py-2 text-right font-medium">Target</th>
              <th className="py-2 text-right font-medium">%</th>
              <th className="py-2 text-right font-medium">Data</th>
            </tr>
          </thead>
          <tbody>
            {d.nutrients.map((n) => (
              <tr key={n.code} className="border-b last:border-0">
                <th scope="row" className="py-1.5 text-left font-normal">
                  {n.name}
                </th>
                <td className="py-1.5 text-right tabular-nums">{n.amount === undefined ? "Unknown" : amountWithUnit(n.amount, n.unit)}</td>
                <td className="py-1.5 text-right tabular-nums text-muted-foreground">{n.target === undefined ? "—" : amountWithUnit(n.target, n.unit)}</td>
                <td className="py-1.5 text-right tabular-nums">{n.percentOfTarget === undefined ? "—" : `${fmt(n.percentOfTarget)}%`}</td>
                <td className="py-1.5 text-right">
                  <CoverageBadge coverage={n.coverage} withData={n.entriesWithData} entries={n.entries} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </CardContent>
    </Card>
  );
}

function TrendCard({ from, to }: { from: string; to: string }) {
  const nutrients = useNutrients();
  const [code, setCode] = useState("ENERGY_KCAL");
  const trend = useTrend(code, from, to);
  const data = (trend.data?.points ?? []).map((p) => ({ label: format(parseISO(p.date), "d MMM"), amount: p.amount ?? null }));
  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-center justify-between gap-2">
        <div>
          <CardTitle>Nutrient trend</CardTitle>
          <CardDescription>Gaps are days with no data, not zero</CardDescription>
        </div>
        <NativeSelect aria-label="Nutrient" value={code} onChange={(e) => setCode(e.target.value)} className="w-56">
          {nutrients.data?.map((n) => (
            <option key={n.code} value={n.code}>
              {n.name}
            </option>
          ))}
        </NativeSelect>
      </CardHeader>
      <CardContent>
        <div className="h-64" role="img" aria-label="Nutrient trend chart">
          <ResponsiveContainer>
            <LineChart data={data} margin={{ left: 0, right: 8, top: 8 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis dataKey="label" tick={axisTick} interval="preserveStartEnd" />
              <YAxis tick={axisTick} width={48} />
              <Tooltip contentStyle={tooltipStyle} formatter={(raw) => { const v = Number(raw); return amountWithUnit(v, trend.data?.unit ?? ""); }} />
              {trend.data?.target && <ReferenceLine y={trend.data.target} stroke="var(--primary)" strokeDasharray="4 4" label={{ value: "Target", fontSize: 11, fill: "var(--muted-foreground)" }} />}
              {trend.data?.upperLimit && <ReferenceLine y={trend.data.upperLimit} stroke="var(--destructive)" strokeDasharray="4 4" label={{ value: "Upper limit", fontSize: 11, fill: "var(--muted-foreground)" }} />}
              <Line type="monotone" dataKey="amount" name={trend.data?.name ?? ""} stroke="var(--protein)" strokeWidth={2} dot={{ r: 2 }} connectNulls={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  );
}

export default function ReportsPage() {
  const [period, setPeriod] = useState<Period>("week");
  const [anchor, setAnchor] = useState(todayIso());
  const [custom, setCustom] = useState({ from: shiftIso(todayIso(), -29), to: todayIso() });

  const params: ReportParams = useMemo(() => {
    if (period === "month") return { kind: "monthly", month: anchor.slice(0, 7) };
    if (period === "custom") return { kind: "custom", from: custom.from, to: custom.to };
    return { kind: "weekly", date: anchor };
  }, [period, anchor, custom]);
  const report = useReport(params);
  const range = report.data ? { from: report.data.from, to: report.data.to } : { from: anchor, to: anchor };

  const step = (dir: number) => {
    const d = parseISO(anchor);
    if (period === "day") setAnchor(shiftIso(anchor, dir));
    else if (period === "week") setAnchor(format(addWeeks(startOfWeek(d, { weekStartsOn: 1 }), dir), "yyyy-MM-dd"));
    else if (period === "month") setAnchor(format(addMonths(d, dir), "yyyy-MM-dd"));
  };
  const label =
    period === "day" ? prettyDate(anchor) : period === "month" ? format(parseISO(anchor), "MMMM yyyy") : report.data ? `${prettyDate(report.data.from, "d MMM")} – ${prettyDate(report.data.to, "d MMM yyyy")}` : "";
  const canGoForward = period !== "custom" && (period === "day" ? anchor < todayIso() : range.to < todayIso());

  return (
    <>
      <PageHeader title="Reports & analytics" description="Averages, adherence to targets and data completeness. Download any of it as CSV." />
      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <Tabs value={period} onValueChange={(v) => setPeriod(v as Period)}>
          <TabsList>
            <TabsTrigger value="day">Day</TabsTrigger>
            <TabsTrigger value="week">Week</TabsTrigger>
            <TabsTrigger value="month">Month</TabsTrigger>
            <TabsTrigger value="custom">Custom</TabsTrigger>
          </TabsList>
        </Tabs>
        {period === "custom" ? (
          <div className="flex flex-wrap items-end gap-2">
            <Field label="From" htmlFor="r-from">
              <Input id="r-from" type="date" value={custom.from} max={custom.to} onChange={(e) => setCustom((c) => ({ ...c, from: e.target.value }))} />
            </Field>
            <Field label="To" htmlFor="r-to">
              <Input id="r-to" type="date" value={custom.to} min={custom.from} max={todayIso()} onChange={(e) => setCustom((c) => ({ ...c, to: e.target.value }))} />
            </Field>
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <Button variant="outline" size="icon" onClick={() => step(-1)} aria-label="Previous period">
              <ChevronLeft />
            </Button>
            <span className="min-w-44 text-center text-sm font-medium">{label}</span>
            <Button variant="outline" size="icon" onClick={() => step(1)} disabled={!canGoForward} aria-label="Next period">
              <ChevronRight />
            </Button>
          </div>
        )}
      </div>

      <Card className="mb-4">
        <CardContent className="flex flex-wrap items-center gap-2 pt-5">
          <span className="mr-2 text-sm font-medium">Export CSV</span>
          {period === "day" ? (
            <Button variant="outline" size="sm" onClick={() => exportCsv("/api/exports/daily-report.csv", { date: anchor }, `nutritrack-daily-${anchor}.csv`)}>
              <Download /> Daily report
            </Button>
          ) : (
            <>
              <Button variant="outline" size="sm" onClick={() => exportCsv("/api/exports/diary.csv", range, "nutritrack-diary.csv")}>
                <Download /> Food diary
              </Button>
              <Button variant="outline" size="sm" onClick={() => exportCsv("/api/exports/nutrient-summary.csv", range, "nutritrack-nutrients.csv")}>
                <Download /> Nutrient summary
              </Button>
              <Button variant="outline" size="sm" onClick={() => exportCsv("/api/exports/weight.csv", range, "nutritrack-weight.csv")}>
                <Download /> Weight history
              </Button>
            </>
          )}
        </CardContent>
      </Card>

      {period === "day" ? (
        <DayView date={anchor} />
      ) : report.isLoading ? (
        <PageSkeleton />
      ) : report.error ? (
        <ErrorState message={errorMessage(report.error)} onRetry={() => report.refetch()} />
      ) : (
        <div className="grid gap-4">
          <RangeView report={report.data!} />
          <TrendCard from={range.from} to={range.to} />
        </div>
      )}
      <p className="mt-6 text-xs text-muted-foreground">{report.data?.disclaimer}</p>
    </>
  );
}
