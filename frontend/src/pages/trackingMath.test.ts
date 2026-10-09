import { describe, expect, it } from "vitest";
import type { WeightEntry } from "@/lib/types";
import { toMl } from "./WaterPage";
import { rollingAverage } from "./WeightPage";

const w = (date: string, weightKg: number) => ({ id: 1, date, weightKg }) as unknown as WeightEntry;

describe("rollingAverage", () => {
  it("averages the entries in the 7 days up to each date", () => {
    const entries = [w("2026-10-01", 70), w("2026-10-03", 72), w("2026-10-07", 71), w("2026-10-08", 74)];
    const avg = rollingAverage(entries);
    expect(avg[0]).toBe(70);
    expect(avg[1]).toBe(71);
    expect(avg[2]).toBeCloseTo(71);
    // 2026-10-01 drops out of the window for 2026-10-08
    expect(avg[3]).toBeCloseTo((72 + 71 + 74) / 3);
  });
});

describe("toMl", () => {
  it("converts litres and rounds", () => {
    expect(toMl("1.5", "l")).toBe(1500);
    expect(toMl("250.4", "ml")).toBe(250);
  });

  it("rejects empty, zero, negative and too-large amounts", () => {
    expect(toMl("", "ml")).toBeNull();
    expect(toMl("0", "ml")).toBeNull();
    expect(toMl("-5", "ml")).toBeNull();
    expect(toMl("6", "l")).toBeNull();
  });
});
