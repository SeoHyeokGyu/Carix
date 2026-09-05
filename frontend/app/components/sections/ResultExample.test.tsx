import { describe, expect, it } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ResultExample } from "./ResultExample";

describe("ResultExample", () => {
  it("결과 예시 텍스트를 렌더링한다", () => {
    render(<ResultExample />);
    expect(screen.getByText(/구매력 지수/)).toBeInTheDocument();
    expect(screen.getByText(/이직 탐색/)).toBeInTheDocument();
  });

  it("바로 아래에 면책 문구를 렌더링한다", () => {
    render(<ResultExample />);
    expect(
      screen.getByText(
        "수치는 진단 근거와 표본 수를 함께 제공하며, 확정적인 연봉 예측이나 재무 조언으로 표현하지 않습니다.",
      ),
    ).toBeInTheDocument();
  });

  it("상세 공식 설명 보기 버튼 클릭 및 키보드 입력(Enter, Space)으로 툴팁을 토글한다", () => {
    render(<ResultExample />);
    const button = screen.getByTitle("상세 공식 설명 보기");

    // Click to open
    fireEvent.click(button);
    expect(screen.getByText(/계산 공식: 명목소득 지수/)).toBeInTheDocument();

    // Click to close
    fireEvent.click(button);
    expect(
      screen.queryByText(/계산 공식: 명목소득 지수/),
    ).not.toBeInTheDocument();

    // Enter to open
    fireEvent.keyDown(button, { key: "Enter" });
    expect(screen.getByText(/계산 공식: 명목소득 지수/)).toBeInTheDocument();

    // Space to close
    fireEvent.keyDown(button, { key: " " });
    expect(
      screen.queryByText(/계산 공식: 명목소득 지수/),
    ).not.toBeInTheDocument();

    // Escape should not open
    fireEvent.keyDown(button, { key: "Escape" });
    expect(
      screen.queryByText(/계산 공식: 명목소득 지수/),
    ).not.toBeInTheDocument();
  });
});
