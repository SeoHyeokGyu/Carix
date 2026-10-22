import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { CpiView } from "./CpiView";

describe("CpiView", () => {
  it("CPI 결측 칸을 강조한다", () => {
    render(
      <CpiView
        data={{
          codes: ["A", "B"],
          months: [{ month: "202609", values: { A: 118.3 } }],
          missing: [{ month: "202609", codes: ["B"] }],
        }}
      />,
    );
    expect(screen.getByText("118.30")).toBeInTheDocument();
    expect(screen.getByTestId("cpi-202609-B")).toHaveAttribute(
      "data-missing",
      "true",
    );
  });
});
