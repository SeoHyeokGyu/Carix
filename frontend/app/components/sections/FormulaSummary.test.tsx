import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { FormulaSummary } from "./FormulaSummary";

describe("FormulaSummary", () => {
  it("두 계산식 제목과 연봉 격차 부호 규약 note를 렌더링한다", () => {
    render(<FormulaSummary />);
    expect(screen.getByText("구매력 계산")).toBeInTheDocument();
    expect(screen.getByText("스킬 시장가치 계산")).toBeInTheDocument();
    expect(
      screen.getByText(/연봉 격차는 사용자 연봉이 시장 중앙값보다 낮을 때 양수/),
    ).toBeInTheDocument();
  });

  it("공식을 모노스페이스 요소로 렌더링한다", () => {
    render(<FormulaSummary />);
    const formulas = screen.getAllByRole("code");
    expect(formulas.length).toBeGreaterThan(0);
  });
});
