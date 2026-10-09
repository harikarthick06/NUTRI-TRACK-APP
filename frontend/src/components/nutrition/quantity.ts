import type { FoodDetail, Portion, WeightState } from "@/lib/types";

export const MASS: Record<string, number> = { G: 1, KG: 1000, MG: 0.001, OZ: 28.349523125, LB: 453.59237 };
export const VOLUME: Record<string, number> = { ML: 1, L: 1000, CUP: 240, TBSP: 15, TSP: 5 };
const UNIT_NAMES: Record<string, string> = {
  G: "g",
  KG: "kg",
  MG: "mg",
  OZ: "oz",
  LB: "lb",
  ML: "mL",
  L: "L",
  CUP: "cup (240 mL)",
  TBSP: "tbsp (15 mL)",
  TSP: "tsp (5 mL)",
  PIECE: "piece",
  SERVING: "serving",
  SLICE: "slice",
  BOWL: "bowl",
};

export function unitName(unit: string): string {
  return UNIT_NAMES[unit] ?? unit.toLowerCase();
}

export interface UnitOption {
  /** Value for the select: a unit code, or "portion:<id>". */
  value: string;
  label: string;
  unit: string;
  portionId?: number;
}

/** Portions first (most natural for Indian dishes), then mass/volume units the food supports. */
export function unitOptions(food: FoodDetail): UnitOption[] {
  const opts: UnitOption[] = food.portions
    .filter((p) => p.id !== undefined)
    .map((p) => ({ value: `portion:${p.id}`, label: p.label, unit: p.unit, portionId: p.id }));
  for (const u of food.supportedUnits) {
    if (u in MASS || u in VOLUME) opts.push({ value: u, label: unitName(u), unit: u });
  }
  return opts;
}

export function defaultUnitOption(food: FoodDetail): UnitOption {
  const opts = unitOptions(food);
  const def = food.portions.find((p) => p.isDefault && p.id !== undefined);
  return opts.find((o) => o.portionId === def?.id) ?? opts.find((o) => o.value === food.basisUnit) ?? opts[0];
}

function density(portions: Portion[]): number | null {
  const p = portions.find((x) => x.unit in VOLUME);
  return p ? p.basisAmount / (p.unitQuantity * VOLUME[p.unit]) : null;
}

/**
 * Amount of the food's basis (g or mL) for a quantity. Mirrors the server's QuantityResolver so the preview
 * matches what will be saved; returns null when the server would reject the combination.
 */
export function basisAmount(food: FoodDetail, quantity: number, option: UnitOption, weightState: WeightState): number | null {
  if (!(quantity > 0)) return null;
  if (weightState === "COOKED_WEIGHT_OF_RAW_FOOD") {
    if (!(option.unit in MASS) || food.basisUnit !== "G" || !food.cookedYieldFactor) return null;
    return (quantity * MASS[option.unit]) / food.cookedYieldFactor;
  }
  if (option.portionId !== undefined) {
    const p = food.portions.find((x) => x.id === option.portionId);
    return p ? (quantity * p.basisAmount) / p.unitQuantity : null;
  }
  if (option.unit in MASS) {
    const g = quantity * MASS[option.unit];
    if (food.basisUnit === "G") return g;
    const d = density(food.portions);
    return d ? g / d : null;
  }
  if (option.unit in VOLUME) {
    const ml = quantity * VOLUME[option.unit];
    if (food.basisUnit === "ML") return ml;
    const exact = food.portions.find((x) => x.unit === option.unit);
    if (exact) return (quantity * exact.basisAmount) / exact.unitQuantity;
    const d = density(food.portions);
    return d ? ml * d : null;
  }
  return null;
}

/** Scales a per-basis nutrient to the logged amount; unknown stays unknown. */
export function scaled(food: FoodDetail, code: string, basis: number | null): number | null {
  if (basis === null) return null;
  const n = food.nutrients.find((x) => x.code === code);
  if (!n || n.amount === undefined || n.amount === null) return null;
  return (n.amount * basis) / food.basisQuantity;
}

export function canUseCookedWeight(food: FoodDetail): boolean {
  return (food.preparationState === "RAW" || food.preparationState === "DRY") && !!food.cookedYieldFactor && food.basisUnit === "G";
}
