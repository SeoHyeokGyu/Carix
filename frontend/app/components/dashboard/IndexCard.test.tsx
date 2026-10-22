import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { IndexCard, toBaselineBar } from "./IndexCard";

describe("IndexCard", () => {
  it("OK 면 값과 전월 대비 포인트를 보여준다", () => {
    render(
      <IndexCard
        title="구매력 지수"
        index={{
          value: 94.2,
          changeFromLastMonth: -1.2,
          status: "DECREASED",
          indexStatus: "OK",
        }}
      />,
    );
    expect(screen.getByText("94.2")).toBeInTheDocument();
    expect(screen.getByText("▼ 1.2p")).toBeInTheDocument();
  });

  // `queryByText("0")`/`queryByText("100")` 은 실제 회귀를 못 잡는다 — 컴포넌트가
  // `value.toFixed(1)` 을 거치므로 `value ?? 0` 류 회귀는 "0" 이 아니라 "0.0" 을
  // 렌더링해 두 문자열 단언을 그대로 통과한다. 진짜 보장은 구조적이다 — `IndexCard`
  // 는 `value !== null` 로만 값 블록을 게이트하지 `indexStatus` 로 게이트하지 않으므로,
  // 어떤 열화 상태에서도 숫자 자체가 아예 렌더되지 않아야 한다. `container.textContent`
  // 에 숫자가 하나도 없는지를 직접 본다 — RTL 의 `queryByText` 는 부모·자식 노드가
  // 같은 텍스트를 가지면 다중 매치로 터질 수 있어 이 목적엔 맞지 않는다.
  it("표본이 부족하면 값 대신 뱃지를 보여주고 숫자 값을 렌더링하지 않는다", () => {
    const { container } = render(
      <IndexCard
        title="시장가치 지수"
        index={{
          value: null,
          changeFromLastMonth: null,
          status: null,
          indexStatus: "INSUFFICIENT_SAMPLE",
        }}
      />,
    );
    expect(screen.getByText("표본 부족")).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/\d/);
  });

  // 값이 있는 상태다 — NOT_AVAILABLE 처럼 값을 숨기거나 "계산 대기"로 읽히면 안 된다.
  it("비교 기간이 짧으면 값을 그리고 비교 기간 짧음으로 표시한다", () => {
    render(
      <IndexCard
        title="구매력 지수"
        index={{
          value: 98.0,
          changeFromLastMonth: null,
          status: null,
          indexStatus: "INSUFFICIENT_HISTORY",
        }}
      />,
    );
    expect(screen.getByText("98.0")).toBeInTheDocument();
    expect(screen.getByText("비교 기간 짧음")).toBeInTheDocument();
    expect(screen.queryByText("계산 대기")).not.toBeInTheDocument();
  });
});

describe("toBaselineBar", () => {
  it("100 보다 크면 가운데에서 오른쪽으로 뻗는다", () => {
    const bar = toBaselineBar(104.6);
    expect(bar.start).toBe(50);
    expect(bar.width).toBeCloseTo(4.6);
  });

  it("범위를 넘으면 끝에 붙인다", () => {
    expect(toBaselineBar(180)).toEqual({ start: 50, width: 50 });
    expect(toBaselineBar(20)).toEqual({ start: 0, width: 50 });
  });

  it("정확히 100 이면 막대 폭이 0", () => {
    expect(toBaselineBar(100)).toEqual({ start: 50, width: 0 });
  });
});
