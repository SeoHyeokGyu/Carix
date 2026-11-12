import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { SeveranceBlock } from "./SeveranceBlock";

describe("SeveranceBlock", () => {
  // DC형은 회사가 이미 적립한 돈이라 시점별 금액을 보이면 퇴사 시점을 잘못 고르게 된다.
  it("DC형이면 금액 없이 적립 안내만 보인다", () => {
    render(
      <SeveranceBlock
        severance={{ pensionType: "DC", status: "DC", points: [] }}
      />,
    );
    expect(
      screen.getByText(
        "회사가 매년 적립해 둔 돈이라 퇴사 시점과 거의 무관합니다. 적립금은 퇴직연금 계좌에서 확인하세요.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/오늘 퇴사/)).not.toBeInTheDocument();
    expect(screen.queryByText(/평균임금/)).not.toBeInTheDocument();
  });
});
