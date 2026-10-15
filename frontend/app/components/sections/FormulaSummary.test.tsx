import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { FormulaSummary } from "./FormulaSummary";

describe("FormulaSummary", () => {
  it("두 계산식 제목과 연봉 격차 부호 규약 note를 렌더링한다", () => {
    render(<FormulaSummary />);
    expect(screen.getByText("구매력 계산")).toBeInTheDocument();
    expect(screen.getByText("스킬 시장가치 계산")).toBeInTheDocument();
    expect(
      screen.getByText(
        /연봉 격차는 사용자 연봉이 시장 중앙값보다 낮을 때 양수/,
      ),
    ).toBeInTheDocument();
  });

  it("공식을 모노스페이스 요소로 렌더링한다", () => {
    render(<FormulaSummary />);
    const formulas = screen.getAllByRole("code");
    expect(formulas.length).toBeGreaterThan(0);
  });

  it("물음표 버튼 클릭 및 키보드 입력(Enter, Space)으로 공식을 토글한다", () => {
    render(<FormulaSummary />);
    const buttons = screen.getAllByTitle("상세 공식 보기");
    const toggleButton = buttons[0];

    // Click to toggle
    fireEvent.click(toggleButton);

    // Enter keydown to toggle
    fireEvent.keyDown(toggleButton, { key: "Enter" });

    // Space keydown to toggle
    fireEvent.keyDown(toggleButton, { key: " " });

    // Other key should not toggle
    fireEvent.keyDown(toggleButton, { key: "Escape" });
  });
});
