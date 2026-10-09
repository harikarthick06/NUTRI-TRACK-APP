import { useMemo, useState } from "react";
import { CartesianGrid, Legend, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { toast } from "sonner";
import { Pencil, Scale, Trash2 } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState, ErrorState } from "@/components/ui/empty-state";
import { Input, NativeSelect } from "@/components/ui/input";
import { Field } from "@/components/ui/label";
import { PageSkeleton } from "@/components/ui/skeleton";
import { useWeightActions, useWeightHistory } from "@/hooks/queries";
import { errorMessage } from "@/lib/api";
import { prettyDate, shiftIso, todayIso } from "@/lib/dates";
import type { WeightEntry } from "@/lib/types";
import { fmt } from "@/lib/utils";

const RANGES = [
  { days: 30, label: "30 days" },
  { days: 90, label: "90 days" },
  { days: 180, label: "6 months" },
  { days: 365, label: "1 year" },
];

/** Average of the entries in the 7 days up to and including each entry's date. */
export function rollingAverage(entries: WeightEntry[]): (number | null)[] {
  return entries.map((e) => {
    const from = shiftIso(e.date, -6);
    const window = entries.filter((x) => x.date >= from && x.date <= e.date);
    return window.length ? window.reduce((s, x) => s + x.weightKg, 0) / window.length : null;
  });
}

function WeightForm({ entry, onDone }: { entry?: WeightEntry; onDone: () => void }) {
  const { add, update } = useWeightActions();
  const [date, setDate] = useState(entry?.date ?? todayIso());
  const [weight, setWeight] = useState(entry ? String(entry.weightKg) : "");
  const [waist, setWaist] = useState(entry?.waistCm ? String(entry.waistCm) : "");
  const [fat, setFat] = useState(entry?.bodyFatPct ? String(entry.bodyFatPct) : "");
  const [note, setNote] = useState(entry?.note ?? "");
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const w = Number(weight);
    if (!(w >= 20 && w <= 400)) {
      setError("Enter a weight between 20 and 400 kg");
      return;
    }
    setError(null);
    const body = {
      date,
      weightKg: w,
      waistCm: waist ? Number(waist) : null,
      bodyFatPct: fat ? Number(fat) : null,
      note: note.trim() || undefined,
    };
    try {
      if (entry) await update.mutateAsync({ id: entry.id, body });
      else await add.mutateAsync(body);
      toast.success(entry ? "Weight updated" : "Weight logged");
      if (!entry) {
        setWeight("");
        setWaist("");
        setFat("");
        setNote("");
      }
      onDone();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const idp = entry ? `e${entry.id}` : "new";
  return (
    <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6 lg:items-end" noValidate>
      <Field label="Date" htmlFor={`${idp}-date`}>
        <Input id={`${idp}-date`} type="date" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} />
      </Field>
      <Field label="Weight (kg)" htmlFor={`${idp}-w`} error={error ?? undefined}>
        <Input id={`${idp}-w`} type="number" step="0.1" inputMode="decimal" value={weight} onChange={(e) => setWeight(e.target.value)} aria-invalid={!!error} />
      </Field>
      <Field label="Waist (cm, optional)" htmlFor={`${idp}-waist`}>
        <Input id={`${idp}-waist`} type="number" step="0.1" inputMode="decimal" value={waist} onChange={(e) => setWaist(e.target.value)} />
      </Field>
      <Field label="Body fat % (optional)" htmlFor={`${idp}-fat`}>
        <Input id={`${idp}-fat`} type="number" step="0.1" inputMode="decimal" value={fat} onChange={(e) => setFat(e.target.value)} />
      </Field>
      <Field label="Note (optional)" htmlFor={`${idp}-note`}>
        <Input id={`${idp}-note`} value={note} maxLength={255} onChange={(e) => setNote(e.target.value)} />
      </Field>
      <Button type="submit" disabled={add.isPending || update.isPending}>
        {entry ? "Save" : "Log weight"}
      </Button>
    </form>
  );
}

export default function WeightPage() {
  const [days, setDays] = useState(90);
  const to = todayIso();
  const from = shiftIso(to, -(days - 1));
  const history = useWeightHistory(from, to);
  const { remove } = useWeightActions();
  const [editing, setEditing] = useState<WeightEntry | null>(null);
  const [deleting, setDeleting] = useState<WeightEntry | null>(null);

  const chart = useMemo(() => {
    const entries = history.data?.entries ?? [];
    const avg = rollingAverage(entries);
    return entries.map((e, i) => ({ date: e.date, label: prettyDate(e.date, "d MMM"), weight: e.weightKg, average: avg[i] }));
  }, [history.data]);

  if (history.isLoading) return <PageSkeleton />;
  if (history.error) return <ErrorState message={errorMessage(history.error)} onRetry={() => history.refetch()} />;
  const { stats, entries } = history.data!;

  const cards = [
    { label: "Latest", value: stats.latestKg, sub: stats.latestDate ? prettyDate(stats.latestDate, "d MMM") : "" },
    { label: "7-day average", value: stats.sevenDayAverageKg, sub: "smooths daily ups and downs" },
    { label: `Change (${RANGES.find((r) => r.days === days)?.label})`, value: stats.changeKg, sub: stats.startKg ? `from ${fmt(stats.startKg, 1)} kg` : "", signed: true },
    { label: "To target", value: stats.toTargetKg, sub: stats.targetWeightKg ? `target ${fmt(stats.targetWeightKg, 1)} kg` : "set a target in your profile", signed: true },
  ];

  return (
    <>
      <PageHeader
        title="Weight"
        description="Log once a day, ideally at the same time. Day-to-day changes are mostly water and food, not fat."
        actions={
          <NativeSelect aria-label="Range" value={days} onChange={(e) => setDays(Number(e.target.value))} className="w-36">
            {RANGES.map((r) => (
              <option key={r.days} value={r.days}>
                {r.label}
              </option>
            ))}
          </NativeSelect>
        }
      />

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Log weight</CardTitle>
        </CardHeader>
        <CardContent>
          <WeightForm onDone={() => undefined} />
        </CardContent>
      </Card>

      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {cards.map((c) => (
          <Card key={c.label}>
            <CardContent className="pt-5">
              <p className="text-sm text-muted-foreground">{c.label}</p>
              <p className="text-2xl font-semibold tabular-nums">
                {c.value === undefined ? "—" : `${c.signed && c.value > 0 ? "+" : ""}${fmt(c.value, 1)} kg`}
              </p>
              <p className="text-xs text-muted-foreground">{c.sub}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Trend</CardTitle>
          <CardDescription>Daily entries and the 7-day average</CardDescription>
        </CardHeader>
        <CardContent>
          {chart.length < 2 ? (
            <EmptyState icon={Scale} title="Not enough entries yet" description="Log your weight on at least two days to see a trend." />
          ) : (
            <div className="h-72" role="img" aria-label="Weight trend chart">
              <ResponsiveContainer>
                <LineChart data={chart} margin={{ left: 0, right: 8, top: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="label" tick={{ fontSize: 12, fill: "var(--muted-foreground)" }} />
                  <YAxis domain={["dataMin - 1", "dataMax + 1"]} tick={{ fontSize: 12, fill: "var(--muted-foreground)" }} width={40} />
                  <Tooltip contentStyle={{ background: "var(--card)", border: "1px solid var(--border)", borderRadius: 8 }} formatter={(raw) => { const v = Number(raw); return `${fmt(v, 1)} kg`; }} />
                  <Legend />
                  {stats.targetWeightKg && <ReferenceLine y={stats.targetWeightKg} stroke="var(--primary)" strokeDasharray="4 4" label={{ value: "Target", fill: "var(--muted-foreground)", fontSize: 12 }} />}
                  <Line type="monotone" dataKey="weight" name="Weight" stroke="var(--protein)" dot={{ r: 3 }} strokeWidth={1.5} />
                  <Line type="monotone" dataKey="average" name="7-day average" stroke="var(--primary)" dot={false} strokeWidth={2.5} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Entries</CardTitle>
        </CardHeader>
        <CardContent>
          {entries.length === 0 ? (
            <p className="text-sm text-muted-foreground">No entries in this range.</p>
          ) : (
            <ul className="divide-y">
              {[...entries].reverse().map((e) =>
                editing?.id === e.id ? (
                  <li key={e.id} className="py-3">
                    <WeightForm entry={e} onDone={() => setEditing(null)} />
                    <Button variant="ghost" size="sm" className="mt-2" onClick={() => setEditing(null)}>
                      Cancel
                    </Button>
                  </li>
                ) : (
                  <li key={e.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                    <div>
                      <p className="font-medium">{prettyDate(e.date)}</p>
                      <p className="text-xs text-muted-foreground">
                        {[e.waistCm && `waist ${fmt(e.waistCm, 1)} cm`, e.bodyFatPct && `${fmt(e.bodyFatPct, 1)}% body fat`, e.note].filter(Boolean).join(" · ")}
                      </p>
                    </div>
                    <div className="flex items-center gap-1">
                      <span className="mr-2 font-semibold tabular-nums">{fmt(e.weightKg, 1)} kg</span>
                      <Button variant="ghost" size="icon-sm" aria-label={`Edit entry for ${e.date}`} onClick={() => setEditing(e)}>
                        <Pencil />
                      </Button>
                      <Button variant="ghost" size="icon-sm" aria-label={`Delete entry for ${e.date}`} onClick={() => setDeleting(e)}>
                        <Trash2 />
                      </Button>
                    </div>
                  </li>
                ),
              )}
            </ul>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        title="Delete this weight entry?"
        description={deleting ? `${fmt(deleting.weightKg, 1)} kg on ${prettyDate(deleting.date)}` : ""}
        pending={remove.isPending}
        onConfirm={async () => {
          try {
            await remove.mutateAsync(deleting!.id);
            toast.success("Entry deleted");
            setDeleting(null);
          } catch (err) {
            toast.error(errorMessage(err));
          }
        }}
      />
    </>
  );
}
