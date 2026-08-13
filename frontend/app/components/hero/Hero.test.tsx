import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Hero } from "./Hero";

describe("Hero", () => {
  it("헤딩과 두 문단, 3가지 질문을 렌더링한다", () => {
    render(<Hero />);
    expect(
      screen.getByRole("heading", { level: 1 }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/연봉이 올라도 물가가 더 빠르게 오르면/),
    ).toBeInTheDocument();
    expect(
      screen.getByText("내 실질 구매력은 좋아지고 있는가?"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("시장에서 내 스킬의 가치는 오르고 있는가?"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("지금 남고, 옮기고, 배우는 선택 중 무엇이 유리한가?"),
    ).toBeInTheDocument();
  });

  it("교차 지수 라인 시그니처를 포함한다", () => {
    render(<Hero />);
    expect(screen.getByTestId("index-crossover-line")).toBeInTheDocument();
  });

  it("CTA 버튼을 렌더링하지 않는다", () => {
    render(<Hero />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
