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
});
