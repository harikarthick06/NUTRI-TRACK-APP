import { useEffect, useState } from "react";
import { Search, Star } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { useFoodSearch, useRecentFoods, type FoodScope } from "@/hooks/queries";
import type { FoodSummary } from "@/lib/types";
import { cn, fmt } from "@/lib/utils";
import { DataQualityBadge } from "./badges";

export function useDebounced<T>(value: T, ms = 250): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

export function FoodRow({ food, onSelect, action }: { food: FoodSummary; onSelect: () => void; action?: React.ReactNode }) {
  const portion = food.defaultPortion;
  return (
    <li className="flex items-center gap-2 border-b last:border-0">
      <button
        type="button"
        onClick={onSelect}
        className="flex min-w-0 flex-1 items-center gap-3 px-2 py-3 text-left hover:bg-accent/60 focus-visible:bg-accent"
      >
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">
            {food.name}
            {food.favorite && <Star className="ml-1 inline size-3 fill-current text-carbs" aria-label="Favourite" />}
          </p>
          <p className="truncate text-xs text-muted-foreground">
            {[food.brand, food.category, food.preparationState !== "NOT_APPLICABLE" ? food.preparationState.toLowerCase() : null]
              .filter(Boolean)
              .join(" · ")}
          </p>
          <div className="mt-1 flex flex-wrap gap-1">
            <DataQualityBadge quality={food.dataQuality} />
          </div>
        </div>
        <div className="shrink-0 text-right text-sm">
          <p className="font-medium tabular-nums">{fmt(food.energyKcal)} kcal</p>
          <p className="text-xs text-muted-foreground">
            per {fmt(food.basisQuantity)} {food.basisUnit.toLowerCase()}
          </p>
          {portion && <p className="max-w-32 truncate text-xs text-muted-foreground">{portion.label}</p>}
        </div>
      </button>
      {action}
    </li>
  );
}

const SCOPES: { value: FoodScope; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "RECENT", label: "Recent" },
  { value: "FAVORITES", label: "Favourites" },
  { value: "MINE", label: "My foods" },
];

/** Search box with scope filter; shows recent foods before the user types. */
export function FoodPicker({ onSelect, autoFocus }: { onSelect: (food: FoodSummary) => void; autoFocus?: boolean }) {
  const [q, setQ] = useState("");
  const [scope, setScope] = useState<FoodScope>("ALL");
  const [page, setPage] = useState(0);
  const debounced = useDebounced(q.trim());
  const showRecent = debounced === "" && scope === "ALL";
  const search = useFoodSearch(debounced, scope, "", page);
  const recent = useRecentFoods();
  const items = showRecent ? recent.data?.items ?? [] : search.data?.items ?? [];
  const loading = showRecent ? recent.isLoading : search.isLoading;

  useEffect(() => setPage(0), [debounced, scope]);

  return (
    <div className="grid gap-3">
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <Input
          aria-label="Search foods"
          placeholder="Search foods, e.g. idli, sambar, chicken"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          className="pl-9"
          autoFocus={autoFocus}
        />
      </div>
      <div role="radiogroup" aria-label="Which foods" className="flex flex-wrap gap-1">
        {SCOPES.map((s) => (
          <button
            key={s.value}
            type="button"
            role="radio"
            aria-checked={scope === s.value}
            onClick={() => setScope(s.value)}
            className={cn(
              "rounded-full border px-3 py-1 text-xs",
              scope === s.value ? "border-primary bg-primary/10 text-primary" : "text-muted-foreground",
            )}
          >
            {s.label}
          </button>
        ))}
      </div>
      {showRecent && items.length > 0 && <p className="text-xs font-medium text-muted-foreground">Recently logged</p>}
      {loading ? (
        <div className="grid gap-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">
          {showRecent ? "Start typing to search the food database." : "No foods found. Try another name or create a custom food."}
        </p>
      ) : (
        <ul className="max-h-[50vh] overflow-y-auto rounded-lg border">
          {items.map((f) => (
            <FoodRow key={f.id} food={f} onSelect={() => onSelect(f)} />
          ))}
        </ul>
      )}
      {!showRecent && search.data && search.data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <span className="text-muted-foreground">
            Page {page + 1} of {search.data.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= search.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
