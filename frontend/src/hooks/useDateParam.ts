import { useSearchParams } from "react-router-dom";
import { isIsoDate, todayIso } from "@/lib/dates";

/** The selected date, kept in the URL (?date=YYYY-MM-DD) so it survives reloads and can be linked. */
export function useDateParam(): [string, (d: string) => void] {
  const [params, setParams] = useSearchParams();
  const raw = params.get("date");
  const date = isIsoDate(raw) ? raw : todayIso();
  const setDate = (d: string) => {
    const next = new URLSearchParams(params);
    if (d === todayIso()) next.delete("date");
    else next.set("date", d);
    setParams(next, { replace: true });
  };
  return [date, setDate];
}
