import { addDays, format, parseISO } from "date-fns";

/** Today's date in the browser's local time zone as YYYY-MM-DD. */
export function todayIso(): string {
  return format(new Date(), "yyyy-MM-dd");
}

export function shiftIso(iso: string, days: number): string {
  return format(addDays(parseISO(iso), days), "yyyy-MM-dd");
}

export function prettyDate(iso: string, pattern = "EEE, d MMM yyyy"): string {
  return format(parseISO(iso), pattern);
}

export function isIsoDate(s: string | null | undefined): s is string {
  return !!s && /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(parseISO(s).getTime());
}
