import { describe, expect, it } from "vitest";
import { amountWithUnit, fmt, pct } from "./utils";

describe("fmt", () => {
  it("shows a dash for unknown values, never zero", () => {
    expect(fmt(undefined)).toBe("—");
    expect(fmt(null)).toBe("—");
    expect(fmt(Number.NaN)).toBe("—");
  });

  it("formats with Indian digit grouping", () => {
    expect(fmt(123456)).toBe("1,23,456");
    expect(fmt(1.26, 1)).toBe("1.3");
  });
});

describe("pct", () => {
  it("returns null without a target", () => {
    expect(pct(10, undefined)).toBeNull();
    expect(pct(10, 0)).toBeNull();
    expect(pct(undefined, 100)).toBeNull();
    expect(pct(25, 50)).toBe(50);
  });
});

describe("amountWithUnit", () => {
  it("uses display units", () => {
    expect(amountWithUnit(15, "UG")).toBe("15 µg");
  });
});
