import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** Formats a number for display; null/undefined is shown as an em dash, never as 0. */
export function fmt(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return value.toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: digits });
}

const UNIT_LABELS: Record<string, string> = { KCAL: "kcal", G: "g", MG: "mg", UG: "µg", ML: "mL" };
export function unitLabel(unit: string | null | undefined): string {
  if (!unit) return "";
  return UNIT_LABELS[unit] ?? unit.toLowerCase();
}

export function amountWithUnit(value: number | null | undefined, unit: string, digits?: number): string {
  if (value === null || value === undefined) return "—";
  const d = digits ?? (unit === "KCAL" ? 0 : value < 10 ? 2 : 1);
  return `${fmt(value, d)} ${unitLabel(unit)}`;
}

export function pct(value: number | null | undefined, of: number | null | undefined): number | null {
  if (value === null || value === undefined || !of) return null;
  return (value / of) * 100;
}

export function titleCase(s: string): string {
  return s
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

export function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n));
}
