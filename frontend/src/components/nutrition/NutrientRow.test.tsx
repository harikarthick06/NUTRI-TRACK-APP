import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { NutrientStatus } from "@/lib/types";
import { NutrientRow } from "./NutrientTable";

function status(over: Partial<NutrientStatus> = {}): NutrientStatus {
  return {
    code: "VITAMIN_C",
    name: "Vitamin C",
    unit: "MG",
    category: "VITAMIN",
    amount: 45,
    entriesWithData: 2,
    entries: 2,
    entriesPartial: 0,
    coverage: "COMPLETE",
    target: 90,
    targetType: "RDA",
    targetSource: { name: "NASEM DRI", version: "2000", valueType: "RDA" },
    percentOfTarget: 50,
    upperLimitComparable: true,
    status: "BELOW_TARGET",
    customTarget: false,
    notes: [],
    ...over,
  } as NutrientStatus;
}

const renderRow = (n: NutrientStatus, detailed = false) => render(<ul><NutrientRow n={n} detailed={detailed} /></ul>);

describe("NutrientRow", () => {
  it("shows amount, target, percent and what's left", () => {
    renderRow(status());
    expect(screen.getByText("45 mg")).toBeInTheDocument();
    expect(screen.getByText(/90 mg RDA/)).toBeInTheDocument();
    expect(screen.getByText("50%")).toBeInTheDocument();
    expect(screen.getByText("45 mg to reach the target")).toBeInTheDocument();
  });

  it("shows unknown, not zero, when no food has data", () => {
    renderRow(status({ amount: undefined, entriesWithData: 0, coverage: "NO_DATA", percentOfTarget: undefined, status: "UNKNOWN" }));
    expect(screen.getByText("Unknown")).toBeInTheDocument();
    expect(screen.getByText("No data")).toBeInTheDocument();
    expect(screen.queryByText(/to reach the target/)).not.toBeInTheDocument();
  });

  it("doesn't claim a shortfall when data is partial", () => {
    renderRow(status({ coverage: "PARTIAL", entriesWithData: 1 }));
    expect(screen.getByText("1/2 foods have data")).toBeInTheDocument();
    expect(screen.queryByText(/to reach the target/)).not.toBeInTheDocument();
  });

  it("flags an amount above the upper limit", () => {
    renderRow(status({ amount: 2500, percentOfTarget: 2778, upperLimit: 2000, upperLimitType: "UL", status: "ABOVE_UPPER_LIMIT" }));
    expect(screen.getByText("Above upper limit of 2,000 mg")).toBeInTheDocument();
  });

  it("expands to show the source", async () => {
    renderRow(status());
    expect(screen.queryByText(/NASEM DRI/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Vitamin C/ }));
    expect(screen.getByText(/NASEM DRI/)).toBeInTheDocument();
  });
});
