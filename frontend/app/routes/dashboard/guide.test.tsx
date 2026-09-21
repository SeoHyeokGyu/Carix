import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import DashboardGuide from "./guide";

describe("DashboardGuide (지표 가이드 및 도움말)", () => {
  it("3대 핵심 지표와 결정 신호 가이드를 정상 렌더링한다", () => {
    render(<DashboardGuide />);

    // 메인 타이틀
    expect(
      screen.getByRole("heading", { name: /지표 해석 및 결정 신호 가이드/ }),
    ).toBeInTheDocument();

    // 3대 지표 카드 확인
    expect(screen.getByText("구매력 지수 (PPI)")).toBeInTheDocument();
    expect(screen.getByText("시장가치 지수 (MVI)")).toBeInTheDocument();
    expect(screen.getByText("개방창 지수 (OWI)")).toBeInTheDocument();

    // 결정 신호 4종 확인
    expect(screen.getByText("이직 탐색")).toBeInTheDocument();
    expect(screen.getByText("연봉 협상")).toBeInTheDocument();
    expect(screen.getByText("스킬 강화")).toBeInTheDocument();
    expect(screen.getByText("현재 유지")).toBeInTheDocument();

    // FAQ 섹션 확인
    expect(screen.getByText(/자주 묻는 질문/)).toBeInTheDocument();
  });
});
