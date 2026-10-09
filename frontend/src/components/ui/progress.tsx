import * as ProgressPrimitive from "@radix-ui/react-progress";
import { cn, clamp } from "@/lib/utils";

export function Progress({
  value,
  className,
  indicatorClassName,
  label,
}: {
  value: number | null | undefined;
  className?: string;
  indicatorClassName?: string;
  label?: string;
}) {
  const v = value === null || value === undefined ? 0 : clamp(value, 0, 100);
  return (
    <ProgressPrimitive.Root
      value={v}
      aria-label={label}
      className={cn("relative h-2 w-full overflow-hidden rounded-full bg-muted", className)}
    >
      <ProgressPrimitive.Indicator
        className={cn("h-full rounded-full bg-primary transition-all", indicatorClassName)}
        style={{ width: `${v}%` }}
      />
    </ProgressPrimitive.Root>
  );
}
