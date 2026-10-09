import { ChevronDown } from "lucide-react";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import type { NutrientStatus } from "@/lib/types";
import { amountWithUnit, cn, fmt } from "@/lib/utils";
import { CoverageBadge } from "./badges";

const TYPE_LABELS: Record<string, string> = {
  RDA: "RDA",
  AI: "AI",
  UL: "Upper limit",
  CDRR: "Reduce if above",
  LIMIT_PCT_ENERGY: "Limit",
  CUSTOM: "Your target",
  PROFILE: "Your target",
};

export function typeLabel(t?: string) {
  return t ? TYPE_LABELS[t] ?? t : "";
}

/** Bar colour: neutral normally, amber when data is incomplete, red only above a comparable upper limit. */
function barClass(n: NutrientStatus) {
  if (n.status === "ABOVE_UPPER_LIMIT") return "bg-destructive";
  if (n.coverage === "PARTIAL" || n.coverage === "NO_DATA") return "bg-warning";
  return "bg-primary";
}

export function remaining(n: NutrientStatus): number | null {
  if (n.status !== "BELOW_TARGET" || n.coverage !== "COMPLETE" || n.amount === undefined || n.target === undefined) return null;
  return n.target - n.amount;
}

export function NutrientRow({ n, detailed }: { n: NutrientStatus; detailed: boolean }) {
  const [open, setOpen] = useState(false);
  const rem = remaining(n);
  const showDetails = detailed || open;
  return (
    <li className="py-3">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <button
          type="button"
          onClick={() => setOpen((o) => !o)}
          aria-expanded={showDetails}
          className="flex items-center gap-1 text-left font-medium"
        >
          {n.name}
          <ChevronDown className={cn("size-3 text-muted-foreground transition-transform", showDetails && "rotate-180")} aria-hidden />
        </button>
        <p className="text-sm tabular-nums">
          <span className="font-semibold">{n.amount === undefined ? "Unknown" : amountWithUnit(n.amount, n.unit)}</span>
          {n.target !== undefined && (
            <span className="text-muted-foreground">
              {" "}
              / {amountWithUnit(n.target, n.unit)} {typeLabel(n.targetType)}
            </span>
          )}
          {n.percentOfTarget !== undefined && <span className="ml-2 text-muted-foreground">{fmt(n.percentOfTarget)}%</span>}
        </p>
      </div>
      {n.target !== undefined && (
        <Progress value={n.percentOfTarget ?? 0} indicatorClassName={barClass(n)} className="mt-2" label={`${n.name} compared with target`} />
      )}
      <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
        <CoverageBadge coverage={n.coverage} withData={n.entriesWithData} entries={n.entries} />
        {rem !== null && <span>{amountWithUnit(rem, n.unit)} to reach the target</span>}
        {n.status === "ABOVE_UPPER_LIMIT" && (
          <Badge variant="destructive">
            Above {typeLabel(n.upperLimitType).toLowerCase()} of {amountWithUnit(n.upperLimit, n.unit)}
          </Badge>
        )}
        {n.customTarget && <Badge variant="outline">Custom</Badge>}
      </div>
      {showDetails && (
        <div className="mt-2 grid gap-1 rounded-md bg-muted/50 p-2 text-xs text-muted-foreground">
          {n.formNote && <p>Measured as {n.formNote}.</p>}
          {n.upperLimit !== undefined && (
            <p>
              {typeLabel(n.upperLimitType)}: {amountWithUnit(n.upperLimit, n.unit)}
              {n.upperLimitAppliesTo && n.upperLimitAppliesTo !== "TOTAL_INTAKE" && " (supplements or one form only)"}
            </p>
          )}
          {n.targetSource && (
            <p>
              Source:{" "}
              {n.targetSource.url ? (
                <a className="text-primary hover:underline" href={n.targetSource.url} target="_blank" rel="noreferrer noopener">
                  {n.targetSource.name}
                </a>
              ) : (
                n.targetSource.name
              )}{" "}
              ({n.targetSource.version}){n.targetSource.note && `. ${n.targetSource.note}`}
            </p>
          )}
          {n.notes.map((note) => (
            <p key={note}>{note}</p>
          ))}
        </div>
      )}
    </li>
  );
}
