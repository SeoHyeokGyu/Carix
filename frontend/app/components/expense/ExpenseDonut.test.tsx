import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ExpenseDonut, toShares } from "./ExpenseDonut";

describe("ExpenseDonut", () => {
  it("금액을 비중으로 바꾼다", () => {
    expect(
      toShares([
        { category: "HOUSING", amount: 750_000 },
        { category: "FOOD", amount: 250_000 },
      ]),
    ).toEqual([
      { category: "HOUSING", share: 75 },
      { category: "FOOD", share: 25 },
    ]);
  });

  it("합계가 0 이면 빈 배열을 돌려준다 — 0 으로 나누지 않는다", () => {
    expect(toShares([{ category: "HOUSING", amount: 0 }])).toEqual([]);
  });

  it("비중을 텍스트로도 읽을 수 있게 표시한다", () => {
    render(<ExpenseDonut items={[{ category: "HOUSING", amount: 100 }]} />);
    expect(screen.getByText("주거·공공요금 100%")).toBeInTheDocument();
  });

  it("금액이 0 인 카테고리는 목록·도넛 어디에도 나오지 않는다", () => {
    render(
      <ExpenseDonut
        items={[
          { category: "HOUSING", amount: 100 },
          { category: "FOOD", amount: 0 },
        ]}
      />,
    );
    expect(screen.getByText("주거·공공요금 100%")).toBeInTheDocument();
    expect(screen.queryByText(/식비/)).not.toBeInTheDocument();
  });

  it("입력한 금액이 전부 0 이면 안내 문구를 보여준다", () => {
    render(<ExpenseDonut items={[{ category: "HOUSING", amount: 0 }]} />);
    expect(screen.getByText(/비중을 계산할 수 없습니다/)).toBeInTheDocument();
  });

  // 팀 리드 판정 — 서버 보간(DEFAULT_CPI) 값도 도넛에 반영하되, 실제 입력값과는
  // 항상 구분해야 한다(프로젝트 공통 규칙: 추정·대체 값은 색만으로 구분하지
  // 않는다). toShares 는 estimated 플래그를 그대로 옮기고, 컴포넌트는 텍스트로도
  // "(추정)"을 붙인다.
  it("estimated 항목은 비중 계산에는 포함되고 텍스트로 구분된다", () => {
    expect(
      toShares([
        { category: "HOUSING", amount: 750_000 },
        { category: "FOOD", amount: 250_000, estimated: true },
      ]),
    ).toEqual([
      { category: "HOUSING", share: 75, estimated: undefined },
      { category: "FOOD", share: 25, estimated: true },
    ]);

    render(
      <ExpenseDonut
        items={[{ category: "FOOD", amount: 100, estimated: true }]}
      />,
    );
    expect(screen.getByText("식비 100% (추정)")).toBeInTheDocument();
  });

  it("estimated 가 아닌 항목에는 「(추정)」 문구가 붙지 않는다", () => {
    render(<ExpenseDonut items={[{ category: "HOUSING", amount: 100 }]} />);
    expect(screen.queryByText(/추정/)).not.toBeInTheDocument();
  });
});
