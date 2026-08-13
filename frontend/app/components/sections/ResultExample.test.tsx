import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
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
});
