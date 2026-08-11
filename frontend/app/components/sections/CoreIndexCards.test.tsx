import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { CoreIndexCards } from "./CoreIndexCards";

describe("CoreIndexCards", () => {
  it("3개 카드의 제목과 설명을 렌더링한다", () => {
    render(<CoreIndexCards />);
    expect(screen.getByText("구매력 지수")).toBeInTheDocument();
    expect(screen.getByText("시장가치 지수")).toBeInTheDocument();
    expect(screen.getByText("결정 신호")).toBeInTheDocument();
    expect(
      screen.getByText(/연봉 상승률에서 개인 소비구조를 반영한/),
    ).toBeInTheDocument();
  });
});
