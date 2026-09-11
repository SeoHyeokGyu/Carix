import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { IndexCard } from "./IndexCard";

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

  it("업데이트 지연은 지연 뱃지로 표시한다", () => {
    render(
      <IndexCard
        title="구매력 지수"
        index={{
          value: 91.0,
          changeFromLastMonth: null,
          status: null,
          indexStatus: "UPDATE_DELAYED",
        }}
      />,
    );
    expect(screen.getByText("업데이트 지연")).toBeInTheDocument();
  });

  it("계산 전 상태는 계산 대기로 표시한다", () => {
    render(
      <IndexCard
        title="시장가치 지수"
        index={{
          value: null,
          changeFromLastMonth: null,
          status: null,
          indexStatus: "NOT_AVAILABLE",
        }}
      />,
    );
    expect(screen.getByText("계산 대기")).toBeInTheDocument();
  });

  // NOT_AVAILABLE 과 같은 "계산 대기" 라벨로 묶이지만, 매핑 표의 별도 키다 — 따로
  // 두지 않으면 이 키가 지워지거나 잘못된 문구로 바뀌어도 타입 체크만 통과하고
  // 아무 테스트도 못 잡는다.
  it("계산 이력이 부족하면 계산 대기로 표시한다", () => {
    render(
      <IndexCard
        title="구매력 지수"
        index={{
          value: null,
          changeFromLastMonth: null,
          status: null,
          indexStatus: "INSUFFICIENT_HISTORY",
        }}
      />,
    );
    expect(screen.getByText("계산 대기")).toBeInTheDocument();
  });
});
