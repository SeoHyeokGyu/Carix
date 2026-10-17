import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";

import type { PpiInputs } from "../../lib/ppiInputs";
import { PpiInputLine } from "./PpiInputLine";

function renderLine(inputs: PpiInputs | null) {
  return render(
    <MemoryRouter>
      <PpiInputLine inputs={inputs} />
    </MemoryRouter>,
  );
}

describe("PpiInputLine", () => {
  it("세전 연봉과 월 생활비를 만원 단위로 보이고 입력 관리로 보낸다", () => {
    renderLine({
      grossSalary: 52_000_000,
      expense: { monthlyTotal: 2_300_000, hasDefaultFill: false },
    });
    expect(
      screen.getByText("세전 연봉 5,200만원 · 월 생활비 230만원"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "연봉·생활비 수정" }),
    ).toHaveAttribute("href", "/mypage/records");
  });

  it("평균으로 채운 카테고리가 있으면 밝힌다", () => {
    renderLine({
      grossSalary: 52_000_000,
      expense: { monthlyTotal: 2_500_000, hasDefaultFill: true },
    });
    expect(
      screen.getByText("세전 연봉 5,200만원 · 월 생활비 250만원(일부 평균)"),
    ).toBeInTheDocument();
  });

  it("값이 없는 쪽은 0 이 아니라 미입력이다", () => {
    renderLine({ grossSalary: null, expense: null });
    expect(
      screen.getByText("세전 연봉 미입력 · 월 생활비 미입력"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/0만원/)).not.toBeInTheDocument();
  });

  it("조회에 실패해 입력값이 없으면 줄 자체가 없다", () => {
    const { container } = renderLine(null);
    expect(container).toBeEmptyDOMElement();
  });
});
