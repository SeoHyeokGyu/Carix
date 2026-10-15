import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { expectHeadingOutline } from "../../test-utils/headingOutline";
import DashboardGuide from "./guide";

describe("DashboardGuide (지표 가이드 및 도움말)", () => {
  it("3대 핵심 지표와 결정 신호 가이드를 정상 렌더링한다", () => {
    render(<DashboardGuide />);

    // 메인 타이틀
    expect(
      screen.getByRole("heading", { level: 1, name: "도움말" }),
    ).toBeInTheDocument();

    expectHeadingOutline();
    expect(
      screen.getByRole("heading", {
        level: 2,
        name: "지표 3종 (기준선 100의 의미)",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 3, name: "구매력 지수 (PPI)" }),
    ).toBeInTheDocument();

    // 3대 지표 카드 확인
    expect(screen.getByText("구매력 지수 (PPI)")).toBeInTheDocument();
    expect(screen.getByText("시장가치 지수 (MVI)")).toBeInTheDocument();
    expect(screen.getByText("개방창 지수 (OWI)")).toBeInTheDocument();

    // 결정 신호 5종 — 판정 화면과 같은 어휘(decisionSignal.ts)로 보인다
    const badges = screen
      .getAllByTestId("signal-badge")
      .map((badge) => badge.textContent);
    // 엔진이 데이터 부족을 맨 먼저 확인한다 — 「위에서부터 차례로」 순서가 엔진과 같다.
    expect(badges).toEqual([
      "데이터 부족",
      "이직 탐색",
      "협상 검토",
      "스킬 보강 우선",
      "현 상태 유지",
    ]);

    // FAQ 섹션 확인
    expect(screen.getByText(/자주 묻는 질문/)).toBeInTheDocument();
  });

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
