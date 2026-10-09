import { Card, CardContent } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import type { MacroTotal } from "@/lib/types";
import { cn, fmt, pct } from "@/lib/utils";

/** Consumed vs target for one macro, flagging when some foods had no data. */
export function MacroCard({
  label,
  total,
  target,
  unit,
  colorClass,
}: {
  label: string;
  total: MacroTotal | undefined;
  target?: number;
  unit: string;
  colorClass: string;
}) {
  const amount = total?.amount;
  const p = pct(amount, target);
  const partial = !!total && total.entries > 0 && total.entriesWithData < total.entries;
  return (
    <Card>
      <CardContent className="grid gap-2 pt-5">
        <div className="flex items-baseline justify-between">
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          {p !== null && <p className="text-xs text-muted-foreground">{fmt(p)}%</p>}
        </div>
        <p className="text-2xl font-semibold tabular-nums">
          {fmt(amount, 1)}
          <span className="text-sm font-normal text-muted-foreground">
            {" "}
            / {target ? `${fmt(target)} ${unit}` : "no target"}
          </span>
        </p>
        <Progress value={p} indicatorClassName={cn(colorClass)} label={`${label} progress`} />
        {partial && (
          <p className="text-xs text-warning-foreground dark:text-warning">
            {total!.entriesWithData} of {total!.entries} foods have data
          </p>
        )}
      </CardContent>
    </Card>
  );
}
