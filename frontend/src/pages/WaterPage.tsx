import { useState } from "react";
import { Bar, BarChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { toast } from "sonner";
import { Droplets, Pencil, Trash2 } from "lucide-react";
import { format, parseISO } from "date-fns";
import { Link } from "react-router-dom";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ErrorState } from "@/components/ui/empty-state";
import { Input, NativeSelect } from "@/components/ui/input";
import { PageSkeleton } from "@/components/ui/skeleton";
import { DateNav } from "@/components/nutrition/DateNav";
import { ProgressRing } from "@/components/nutrition/ProgressRing";
import { useWaterActions, useWaterDay, useWaterHistory } from "@/hooks/queries";
import { useDateParam } from "@/hooks/useDateParam";
import { errorMessage } from "@/lib/api";
import { prettyDate, shiftIso } from "@/lib/dates";
import type { WaterEntry } from "@/lib/types";
import { fmt } from "@/lib/utils";

const QUICK = [150, 250, 500, 750, 1000];

export function toMl(value: string, unit: "ml" | "l"): number | null {
  const n = Number(value);
  if (!(n > 0)) return null;
  const ml = Math.round(unit === "l" ? n * 1000 : n);
  return ml >= 1 && ml <= 5000 ? ml : null;
}

export default function WaterPage() {
  const [date, setDate] = useDateParam();
  const day = useWaterDay(date);
  const history = useWaterHistory(shiftIso(date, -29), date);
  const { add, update, remove } = useWaterActions();
  const [amount, setAmount] = useState("");
  const [unit, setUnit] = useState<"ml" | "l">("ml");
  const [editing, setEditing] = useState<WaterEntry | null>(null);
  const [editAmount, setEditAmount] = useState("");
  const [deleting, setDeleting] = useState<WaterEntry | null>(null);

  if (day.isLoading) return <PageSkeleton />;
  if (day.error) return <ErrorState message={errorMessage(day.error)} onRetry={() => day.refetch()} />;
  const d = day.data!;

  const addMl = async (ml: number) => {
    try {
      await add.mutateAsync({ date, amountMl: ml });
      toast.success(`Added ${ml} mL`);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const addCustom = async (e: React.FormEvent) => {
    e.preventDefault();
    const ml = toMl(amount, unit);
    if (ml === null) {
      toast.error("Enter between 1 mL and 5 L");
      return;
    }
    await addMl(ml);
    setAmount("");
  };

  const chart = (history.data?.days ?? []).map((x) => ({ label: format(parseISO(x.date), "d MMM"), ml: x.totalMl }));

  return (
    <>
      <PageHeader title="Water" description="Plain drinking water. Water in food, tea and milk is tracked separately." actions={<DateNav date={date} onChange={setDate} />} />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>{prettyDate(date)}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col items-center gap-4">
            <ProgressRing value={d.percentOfTarget ?? 0} size={160} stroke={14} colorClass="text-water" label={`${d.totalMl} mL of water`}>
              <div>
                <Droplets className="mx-auto size-5 text-water" aria-hidden />
                <p className="text-2xl font-semibold tabular-nums">{d.totalMl >= 1000 ? `${fmt(d.totalMl / 1000, 2)} L` : `${d.totalMl} mL`}</p>
                <p className="text-xs text-muted-foreground">{d.targetMl ? `of ${fmt(d.targetMl / 1000, 2)} L goal` : "no goal set"}</p>
              </div>
            </ProgressRing>
            {!d.targetMl && (
              <Button variant="link" asChild>
                <Link to="/settings">Set a water goal</Link>
              </Button>
            )}
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Add water</CardTitle>
            <CardDescription>A typical glass is 200-250 mL.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            <div className="flex flex-wrap gap-2">
              {QUICK.map((ml) => (
                <Button key={ml} variant="outline" onClick={() => addMl(ml)} disabled={add.isPending}>
                  +{ml >= 1000 ? `${ml / 1000} L` : `${ml} mL`}
                </Button>
              ))}
            </div>
            <form onSubmit={addCustom} className="flex gap-2">
              <Input aria-label="Custom amount" type="number" step="any" min="0" placeholder="Custom amount" value={amount} onChange={(e) => setAmount(e.target.value)} />
              <NativeSelect aria-label="Unit" value={unit} onChange={(e) => setUnit(e.target.value as "ml" | "l")} className="w-24">
                <option value="ml">mL</option>
                <option value="l">L</option>
              </NativeSelect>
              <Button type="submit" disabled={add.isPending}>
                Add
              </Button>
            </form>
            <div>
              <p className="mb-2 text-sm font-medium">Entries</p>
              {d.entries.length === 0 ? (
                <p className="text-sm text-muted-foreground">Nothing logged on this day.</p>
              ) : (
                <ul className="divide-y text-sm">
                  {d.entries.map((e) => (
                    <li key={e.id} className="flex items-center justify-between gap-2 py-2">
                      <span className="text-muted-foreground">{format(new Date(e.loggedAt), "HH:mm")}</span>
                      {editing?.id === e.id ? (
                        <form
                          className="flex flex-1 gap-2"
                          onSubmit={async (ev) => {
                            ev.preventDefault();
                            const ml = toMl(editAmount, "ml");
                            if (ml === null) return toast.error("Enter between 1 and 5000 mL");
                            try {
                              await update.mutateAsync({ id: e.id, amountMl: ml, note: e.note });
                              setEditing(null);
                            } catch (err) {
                              toast.error(errorMessage(err));
                            }
                          }}
                        >
                          <Input aria-label="Amount in mL" type="number" value={editAmount} onChange={(ev) => setEditAmount(ev.target.value)} className="h-8" />
                          <Button size="sm" type="submit">
                            Save
                          </Button>
                          <Button size="sm" variant="ghost" onClick={() => setEditing(null)}>
                            Cancel
                          </Button>
                        </form>
                      ) : (
                        <>
                          <span className="flex-1 font-medium tabular-nums">{e.amountMl} mL</span>
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={`Edit ${e.amountMl} mL entry`}
                            onClick={() => {
                              setEditing(e);
                              setEditAmount(String(e.amountMl));
                            }}
                          >
                            <Pencil />
                          </Button>
                          <Button variant="ghost" size="icon-sm" aria-label={`Delete ${e.amountMl} mL entry`} onClick={() => setDeleting(e)}>
                            <Trash2 />
                          </Button>
                        </>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </CardContent>
        </Card>

        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle>Last 30 days</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="h-60" role="img" aria-label="Daily water intake chart">
              <ResponsiveContainer>
                <BarChart data={chart} margin={{ left: 0, right: 8, top: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                  <XAxis dataKey="label" tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} interval="preserveStartEnd" />
                  <YAxis tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} width={44} />
                  <Tooltip contentStyle={{ background: "var(--card)", border: "1px solid var(--border)", borderRadius: 8 }} formatter={(raw) => { const v = Number(raw); return `${v} mL`; }} />
                  {history.data?.targetMl && <ReferenceLine y={history.data.targetMl} stroke="var(--primary)" strokeDasharray="4 4" />}
                  <Bar dataKey="ml" name="Water" fill="var(--water)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      </div>
      <p className="mt-6 text-xs text-muted-foreground">{d.note}</p>

      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        title="Delete this entry?"
        description={deleting ? `${deleting.amountMl} mL` : ""}
        pending={remove.isPending}
        onConfirm={async () => {
          try {
            await remove.mutateAsync(deleting!.id);
            setDeleting(null);
          } catch (err) {
            toast.error(errorMessage(err));
          }
        }}
      />
    </>
  );
}
