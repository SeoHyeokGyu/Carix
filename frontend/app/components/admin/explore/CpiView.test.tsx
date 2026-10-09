import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CpiView } from "./CpiView";
import type { CpiTable } from "../../../lib/collectorApi";

const sharpTable: CpiTable = {
  codes: ["A", "H"],
  months: [
    { month: "202507", values: { A: 100, H: 101 } },
    { month: "202508", values: { A: 100.5, H: 87.87 } },
  ],
  missing: [],
};

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

  it("CPI 머리글에 분류 이름을 붙이고 급변 칸을 중립색으로 표시한다", () => {
    render(<CpiView data={sharpTable} />);
    expect(screen.getByText("A 식료품·비주류음료")).toBeInTheDocument();
    expect(screen.getByText("H 정보통신")).toBeInTheDocument();
    const sharp = screen.getByTestId("cpi-202508-H");
    expect(sharp).toHaveAttribute("data-sharp", "true");
    expect(sharp).toHaveAttribute("title", "값 확인 — 전월 대비 5% 넘게 변함");
    expect(sharp.className).not.toContain("text-carix-warning");
    expect(screen.getByTestId("cpi-202508-A")).toHaveAttribute(
      "data-sharp",
      "false",
    );
  });

  it("급변 칸에 눈에 보이는 표식과 스크린리더용 문구를 붙이고 다른 칸에는 붙이지 않는다", () => {
    render(<CpiView data={sharpTable} />);
    const sharp = screen.getByTestId("cpi-202508-H");
    const hint = within(sharp).getByText("값 확인 — 전월 대비 5% 넘게 변함");
    expect(hint).toHaveClass("sr-only");
    expect(within(sharp).getByText("!")).toHaveAttribute("aria-hidden", "true");
    const plain = screen.getByTestId("cpi-202508-A");
    expect(within(plain).queryByText("!")).not.toBeInTheDocument();
    expect(
      within(plain).queryByText("값 확인 — 전월 대비 5% 넘게 변함"),
    ).not.toBeInTheDocument();
  });

  it("표 위에 급변·결측 표식의 범례를 보인다", () => {
    render(<CpiView data={sharpTable} />);
    expect(screen.getByText("전월 대비 5% 넘게 변함")).toBeInTheDocument();
    expect(screen.getByText("값 없음")).toBeInTheDocument();
  });

  it("CPI 데이터가 없으면 안내한다", () => {
    render(<CpiView data={{ codes: [], months: [], missing: [] }} />);
    expect(
      screen.getByText("해당 조건의 데이터가 없습니다"),
    ).toBeInTheDocument();
  });
});
