import { describe, expect, it } from "vitest";
import type { FoodDetail } from "@/lib/types";
import { basisAmount, canUseCookedWeight, defaultUnitOption, scaled, unitOptions } from "./quantity";

function food(over: Partial<FoodDetail> = {}): FoodDetail {
  return {
    id: 1,
    name: "Rice, raw",
    basisQuantity: 100,
    basisUnit: "G",
    preparationState: "RAW",
    cookedYieldFactor: 2.5,
    nutrients: [
      { code: "ENERGY_KCAL", amount: 350 },
      { code: "VITAMIN_C" },
    ],
    portions: [
      { id: 7, label: "1 cup cooked", unit: "CUP", unitQuantity: 1, basisAmount: 60, isDefault: true },
      { id: 8, label: "1 tbsp", unit: "TBSP", unitQuantity: 1, basisAmount: 12, isDefault: false },
    ],
    supportedUnits: ["G", "KG", "CUP", "PIECE"],
    ...over,
  } as unknown as FoodDetail;
}

const opt = (f: FoodDetail, value: string) => unitOptions(f).find((o) => o.value === value)!;

describe("unitOptions", () => {
  it("lists portions first, then mass and volume units, and skips unsupported units", () => {
    const values = unitOptions(food()).map((o) => o.value);
    expect(values).toEqual(["portion:7", "portion:8", "G", "KG", "CUP"]);
  });

  it("defaults to the default portion", () => {
    expect(defaultUnitOption(food()).value).toBe("portion:7");
  });
});

describe("basisAmount", () => {
  it("converts mass units", () => {
    const f = food();
    expect(basisAmount(f, 250, opt(f, "G"), "AS_DEFINED")).toBe(250);
    expect(basisAmount(f, 0.5, opt(f, "KG"), "AS_DEFINED")).toBe(500);
  });

  it("uses the portion weight", () => {
    const f = food();
    expect(basisAmount(f, 2, opt(f, "portion:7"), "AS_DEFINED")).toBe(120);
  });

  it("uses an exact portion for a volume unit on a gram-based food", () => {
    const f = food();
    expect(basisAmount(f, 2, opt(f, "CUP"), "AS_DEFINED")).toBe(120);
  });

  it("converts cooked weight back to raw with the yield factor", () => {
    const f = food();
    expect(basisAmount(f, 250, opt(f, "G"), "COOKED_WEIGHT_OF_RAW_FOOD")).toBe(100);
  });

  it("rejects cooked weight when there is no yield factor", () => {
    const f = food({ cookedYieldFactor: undefined });
    expect(basisAmount(f, 250, opt(f, "G"), "COOKED_WEIGHT_OF_RAW_FOOD")).toBeNull();
    expect(canUseCookedWeight(f)).toBe(false);
  });

  it("rejects zero or negative quantities", () => {
    const f = food();
    expect(basisAmount(f, 0, opt(f, "G"), "AS_DEFINED")).toBeNull();
    expect(basisAmount(f, -1, opt(f, "G"), "AS_DEFINED")).toBeNull();
  });

  it("returns null for grams on a mL food with no density", () => {
    const f = food({ basisUnit: "ML", portions: [], supportedUnits: ["ML", "G"] });
    expect(basisAmount(f, 100, opt(f, "G"), "AS_DEFINED")).toBeNull();
  });
});

describe("scaled", () => {
  it("scales a known nutrient", () => {
    expect(scaled(food(), "ENERGY_KCAL", 50)).toBe(175);
  });

  it("keeps a missing value unknown instead of zero", () => {
    expect(scaled(food(), "VITAMIN_C", 50)).toBeNull();
    expect(scaled(food(), "IRON", 50)).toBeNull();
  });
});
