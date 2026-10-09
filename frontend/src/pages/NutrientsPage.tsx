import { useMemo, useState } from "react";
import { ChevronDown, Search } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState, ErrorState } from "@/components/ui/empty-state";
import { Input, NativeSelect } from "@/components/ui/input";
import { PageSkeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { DateNav } from "@/components/nutrition/DateNav";
import { NutrientRow } from "@/components/nutrition/NutrientTable";
import { useMicronutrients } from "@/hooks/queries";
import { useDateParam } from "@/hooks/useDateParam";
import { errorMessage } from "@/lib/api";
import type { TargetStatus } from "@/lib/types";
import { cn } from "@/lib/utils";

const CATEGORIES = [
  { value: "VITAMIN", label: "Vitamins" },
  { value: "MINERAL", label: "Minerals" },
  { value: "FATTY_ACID", label: "Fatty acids" },
  { value: "OTHER", label: "Other nutrients" },
];

const STATUS_FILTERS: { value: "" | TargetStatus | "INCOMPLETE"; label: string }[] = [
  { value: "", label: "All nutrients" },
  { value: "BELOW_TARGET", label: "Below target" },
  { value: "TARGET_REACHED", label: "Target reached" },
  { value: "ABOVE_UPPER_LIMIT", label: "Above upper limit" },
  { value: "INCOMPLETE", label: "Incomplete data" },
  { value: "UNKNOWN", label: "No data" },
];

export default function NutrientsPage() {
  const [date, setDate] = useDateParam();
  const micro = useMicronutrients(date);
  const [q, setQ] = useState("");
  const [category, setCategory] = useState("");
  const [status, setStatus] = useState<(typeof STATUS_FILTERS)[number]["value"]>("");
  const [detailed, setDetailed] = useState(false);
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  const filtered = useMemo(() => {
    const term = q.trim().toLowerCase();
    return (micro.data?.nutrients ?? []).filter((n) => {
      if (category && n.category !== category) return false;
      if (term && !n.name.toLowerCase().includes(term)) return false;
      if (status === "INCOMPLETE") return n.coverage === "PARTIAL" || n.coverage === "NO_DATA";
      if (status) return n.status === status;
      return true;
    });
  }, [micro.data, q, category, status]);

  if (micro.isLoading) return <PageSkeleton />;
  if (micro.error) return <ErrorState message={errorMessage(micro.error)} onRetry={() => micro.refetch()} />;
  const data = micro.data!;
  const above = data.statusCounts.ABOVE_UPPER_LIMIT;

  return (
    <>
      <PageHeader title="Vitamins & minerals" description="Totals from the foods you logged, compared with reference values." actions={<DateNav date={date} onChange={setDate} />} />

      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {data.categories.map((c) => (
          <Card key={c.category}>
            <CardContent className="pt-5">
              <p className="text-sm text-muted-foreground">{CATEGORIES.find((x) => x.value === c.category)?.label}</p>
              <p className="text-xl font-semibold">
                {c.withData}
                <span className="text-sm font-normal text-muted-foreground"> of {c.nutrients} with data</span>
              </p>
            </CardContent>
          </Card>
        ))}
      </div>

      {above > 0 && (
        <p role="status" className="mb-4 rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-sm">
          {above} nutrient{above > 1 ? "s are" : " is"} above an upper limit that applies to total intake. Upper limits are the highest daily intake
          unlikely to cause harm over time; one day above isn't a diagnosis.
        </p>
      )}

      <Card className="mb-4">
        <CardContent className="grid gap-3 pt-5 md:grid-cols-[1fr_auto_auto_auto] md:items-center">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input aria-label="Search nutrients" placeholder="Search, e.g. iron, B12" className="pl-9" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <NativeSelect aria-label="Category" value={category} onChange={(e) => setCategory(e.target.value)} className="md:w-44">
            <option value="">All categories</option>
            {CATEGORIES.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </NativeSelect>
          <NativeSelect aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value as typeof status)} className="md:w-44">
            {STATUS_FILTERS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </NativeSelect>
          <label className="flex items-center gap-2 text-sm">
            <Switch checked={detailed} onCheckedChange={setDetailed} aria-label="Detailed view" />
            Detailed
          </label>
        </CardContent>
      </Card>

      {data.entryCount === 0 ? (
        <EmptyState title="Nothing logged on this day" description="Add foods in the diary to see vitamins and minerals." />
      ) : filtered.length === 0 ? (
        <EmptyState title="No nutrients match" description="Change the search or filters." />
      ) : (
        <div className="grid gap-4">
          {CATEGORIES.filter((c) => filtered.some((n) => n.category === c.value)).map((c) => {
            const rows = filtered.filter((n) => n.category === c.value);
            const isCollapsed = collapsed[c.value];
            return (
              <Card key={c.value}>
                <CardHeader className="pb-0">
                  <CardTitle>
                    <button
                      type="button"
                      className="flex w-full items-center justify-between"
                      aria-expanded={!isCollapsed}
                      onClick={() => setCollapsed((s) => ({ ...s, [c.value]: !s[c.value] }))}
                    >
                      {c.label} <span className="text-sm font-normal text-muted-foreground">({rows.length})</span>
                      <ChevronDown className={cn("ml-auto size-4 transition-transform", !isCollapsed && "rotate-180")} aria-hidden />
                    </button>
                  </CardTitle>
                </CardHeader>
                {!isCollapsed && (
                  <CardContent>
                    <ul className="divide-y">
                      {rows.map((n) => (
                        <NutrientRow key={n.code} n={n} detailed={detailed} />
                      ))}
                    </ul>
                  </CardContent>
                )}
              </Card>
            );
          })}
        </div>
      )}
      <p className="mt-6 text-xs text-muted-foreground">
        {data.disclaimer} Food data rarely covers every nutrient (iodine from iodised salt, for example), so a low number may mean missing data, not a
        low intake. NutriTrack doesn't diagnose deficiencies or recommend supplements.
      </p>
    </>
  );
}
