import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { IndexCard } from "./IndexCard";

describe("IndexCard", () => {
  it("OK 면 값과 전월 대비 포인트를 보여준다", () => {
    render(
      <IndexCard
        title="구매력 지수"
        index={{ value: 94.2, changeFromLastMonth: -1.2, status: "DECREASED", indexStatus: "OK" }}
      />,
    );
    expect(screen.getByText("94.2")).toBeInTheDocument();
    expect(screen.getByText("▼ 1.2p")).toBeInTheDocument();
  });

  it("표본이 부족하면 값 대신 뱃지를 보여주고 0 이나 100 을 쓰지 않는다", () => {
    render(
      <IndexCard
        title="시장가치 지수"
        index={{ value: null, changeFromLastMonth: null, status: null, indexStatus: "INSUFFICIENT_SAMPLE" }}
      />,
    );
    expect(screen.getByText("표본 부족")).toBeInTheDocument();
    expect(screen.queryByText("0")).not.toBeInTheDocument();
    expect(screen.queryByText("100")).not.toBeInTheDocument();
  });

  it("업데이트 지연은 지연 뱃지로 표시한다", () => {
    render(
      <IndexCard
        title="구매력 지수"
        index={{ value: 91.0, changeFromLastMonth: null, status: null, indexStatus: "UPDATE_DELAYED" }}
      />,
    );
    expect(screen.getByText("업데이트 지연")).toBeInTheDocument();
  });

  it("계산 전 상태는 계산 대기로 표시한다", () => {
    render(
      <IndexCard
        title="시장가치 지수"
        index={{ value: null, changeFromLastMonth: null, status: null, indexStatus: "NOT_AVAILABLE" }}
      />,
    );
    expect(screen.getByText("계산 대기")).toBeInTheDocument();
  });
});
