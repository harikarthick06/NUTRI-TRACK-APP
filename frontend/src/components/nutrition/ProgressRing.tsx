import type { ReactNode } from "react";
import { cn, clamp } from "@/lib/utils";

/** Circular progress. Values over 100% show a full ring; the label carries the real number. */
export function ProgressRing({
  value,
  size = 120,
  stroke = 10,
  colorClass = "text-primary",
  children,
  label,
}: {
  value: number | null | undefined;
  size?: number;
  stroke?: number;
  colorClass?: string;
  children?: ReactNode;
  label: string;
}) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const v = value === null || value === undefined ? 0 : clamp(value, 0, 100);
  return (
    <div className="relative inline-grid place-items-center" style={{ width: size, height: size }} role="img" aria-label={label}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="currentColor" strokeWidth={stroke} className="text-muted" />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="currentColor"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={c - (v / 100) * c}
          className={cn("transition-[stroke-dashoffset] duration-500", colorClass)}
        />
      </svg>
      <div className="absolute inset-0 grid place-items-center text-center">{children}</div>
    </div>
  );
}
