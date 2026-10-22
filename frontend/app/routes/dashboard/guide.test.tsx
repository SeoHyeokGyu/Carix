import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { GUIDE_SECTION } from "@/lib/guideSections";

import DashboardGuide from "./guide";

describe("DashboardGuide (지표 가이드 및 도움말)", () => {
  // 대시보드 ⓘ 툴팁의 「도움말에서 자세히 보기」가 이 앵커로 바로 온다.
  it.each(Object.entries(GUIDE_SECTION))(
    "%s 섹션 앵커(#%s)가 고정 헤더 아래로 띄워져 있다",
    (_, id) => {
      const { container } = render(<DashboardGuide />);
      const target = container.querySelector(`#${id}`);
      expect(target).not.toBeNull();
      expect(target).toHaveClass("scroll-mt-24");
    },
  );

  // 판정 기준 문구는 DecisionSignalEngine 의 임계값과 같아야 한다.
  it("결정 신호 판정 기준을 규칙표 임계값으로 적는다", () => {
    render(<DashboardGuide />);

    expect(
      screen.getByText(/연봉 격차 15% 이상.*시장가치 지수 110 이상/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/연봉 격차 5~15%.*시장가치 지수 105 이상/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/시장가치 지수 90 미만.*구매력 지수 95 미만/),
    ).toBeInTheDocument();

    // 데이터 부족은 사용자 입력만이 원인이 아니다 — 수집 공백도 같은 신호를 낸다.
    expect(
      screen.getByText(/임금 기준선.*수집되지 않은 구간/),
    ).toBeInTheDocument();
  });
});
