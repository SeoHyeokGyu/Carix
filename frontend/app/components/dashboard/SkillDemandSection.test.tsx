import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { SkillDemandResponse } from "../../lib/dashboardMarketApi";
import { SkillDemandSection } from "./SkillDemandSection";

describe("SkillDemandSection", () => {
  it("보유 스킬별 수요 지수, 가중치, 추세 뱃지를 올바르게 렌더링한다", () => {
    const mockData: SkillDemandResponse = {
      skills: [
        {
          skillId: "s-1",
          standardName: "Spring Boot",
          demandIndex: 125.4,
          recentDemandRate: 0.15,
          baselineDemandRate: 0.12,
          sources: [
            { source: "SARAMIN", demandIndex: 124.0, sampleSize: 100 },
            { source: "WANTED", demandIndex: 127.0, sampleSize: 50 },
          ],
          weight: 0.65,
          status: "OK",
        },
        {
          skillId: "s-2",
          standardName: "Docker",
          demandIndex: 88.2,
          recentDemandRate: 0.08,
          baselineDemandRate: 0.1,
          sources: [{ source: "SARAMIN", demandIndex: 88.2, sampleSize: 40 }],
          weight: 0.35,
          status: "OK",
        },
      ],
      coverage: "보유 스킬 2개 중 2개 반영 (가중치 100% 재정규화)",
    };

    const { container } = render(<SkillDemandSection data={mockData} />);

    expect(screen.getByTestId("skill-demand-section")).toHaveAttribute(
      "data-slot",
      "card",
    );
    expect(container.querySelector('[data-slot="table"]')).not.toBeNull();
    expect(screen.getByText("보유 스킬 수요 추세")).toHaveAttribute(
      "data-slot",
      "card-title",
    );
    const coveragePill = screen.getByText(/보유 스킬 2개 중 2개 반영/);
    expect(coveragePill).toBeInTheDocument();
    // ToneBadge 를 실제로 거쳤는지 — data-tone 은 ToneBadge 만 찍는다.
    expect(coveragePill).toHaveAttribute("data-tone", "info");
    // cn() 이 rounded-full 로 base 의 rounded 를 실제로 덮어썼는지 (tailwind-merge 병합 검증).
    expect(coveragePill.className).toContain("rounded-full");
    expect(coveragePill.className.split(/\s+/)).not.toContain("rounded");

    // 스킬명 확인
    expect(screen.getByText("Spring Boot")).toBeInTheDocument();
    expect(screen.getByText("65.0%")).toBeInTheDocument();
    expect(screen.getByText(/▲ 상승/)).toBeInTheDocument();
    expect(screen.getByText(/125.4/)).toBeInTheDocument();

    expect(screen.getByText("Docker")).toBeInTheDocument();
    expect(screen.getByText("35.0%")).toBeInTheDocument();
    expect(screen.getByText(/▼ 하락/)).toBeInTheDocument();
    expect(screen.getByText(/88.2/)).toBeInTheDocument();
  });
});
