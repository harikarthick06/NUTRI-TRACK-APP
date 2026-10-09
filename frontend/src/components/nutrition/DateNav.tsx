import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { isIsoDate, prettyDate, shiftIso, todayIso } from "@/lib/dates";

export function DateNav({ date, onChange }: { date: string; onChange: (d: string) => void }) {
  const isToday = date === todayIso();
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Button variant="outline" size="icon" onClick={() => onChange(shiftIso(date, -1))} aria-label="Previous day">
        <ChevronLeft />
      </Button>
      <label className="sr-only" htmlFor="date-nav">
        Date
      </label>
      <Input
        id="date-nav"
        type="date"
        value={date}
        max={todayIso()}
        onChange={(e) => isIsoDate(e.target.value) && onChange(e.target.value)}
        className="w-auto"
      />
      <Button
        variant="outline"
        size="icon"
        onClick={() => onChange(shiftIso(date, 1))}
        disabled={isToday}
        aria-label="Next day"
      >
        <ChevronRight />
      </Button>
      {!isToday && (
        <Button variant="ghost" size="sm" onClick={() => onChange(todayIso())}>
          Today
        </Button>
      )}
      <span className="hidden text-sm text-muted-foreground md:inline">{prettyDate(date)}</span>
    </div>
  );
}
