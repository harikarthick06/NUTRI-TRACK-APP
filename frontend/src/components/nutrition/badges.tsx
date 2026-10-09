import { Badge } from "@/components/ui/badge";
import type { DataCoverage, DataQuality } from "@/lib/types";
import { fmt } from "@/lib/utils";

const QUALITY: Record<DataQuality, { label: string; variant: "default" | "info" | "secondary" | "warning"; title: string }> = {
  VERIFIED: { label: "Verified", variant: "default", title: "From a published food composition table (IFCT 2017 or USDA SR28)" },
  EXTERNAL: { label: "External", variant: "info", title: "Imported from USDA FoodData Central or Open Food Facts" },
  USER_ENTERED: { label: "User entered", variant: "secondary", title: "Entered by a user; not checked" },
  ESTIMATED: { label: "Estimated", variant: "warning", title: "Calculated from ingredients or conversion factors" },
};

export function DataQualityBadge({ quality }: { quality: DataQuality }) {
  const q = QUALITY[quality];
  return (
    <Badge variant={q.variant} title={q.title}>
      {q.label}
    </Badge>
  );
}

/** How many of the tracked nutrients a food has values for. */
export function CompletenessBadge({ pct }: { pct?: number | null }) {
  if (pct === null || pct === undefined) return null;
  const variant = pct >= 75 ? "secondary" : pct >= 40 ? "outline" : "warning";
  return (
    <Badge variant={variant} title="Share of tracked nutrients with data for this food">
      {fmt(pct, 0)}% data
    </Badge>
  );
}

export function CoverageBadge({ coverage, withData, entries }: { coverage: DataCoverage; withData: number; entries: number }) {
  if (coverage === "COMPLETE") return <Badge variant="secondary">All foods have data</Badge>;
  if (coverage === "NO_ENTRIES") return <Badge variant="outline">Nothing logged</Badge>;
  if (coverage === "NO_DATA") return <Badge variant="warning">No data</Badge>;
  if (withData >= entries)
    return (
      <Badge variant="warning" title="Some ingredients of the logged dishes have no value for this nutrient, so the real total is probably higher">
        Incomplete ingredient data
      </Badge>
    );
  return (
    <Badge variant="warning" title="Some logged foods have no value for this nutrient, so the real total is probably higher">
      {withData}/{entries} foods have data
    </Badge>
  );
}
