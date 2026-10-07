import { describe, expect, it } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SkillDemandSection } from "./SkillDemandSection";
import type { SkillDemandResponse } from "../../lib/dashboardMarketApi";

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

    render(<SkillDemandSection data={mockData} />);

    expect(screen.getByText("보유 스킬 수요 추세")).toBeInTheDocument();
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

  it("커스텀 스킬 및 표본 부족 상태 뱃지를 올바르게 표시한다", () => {
    const mockData: SkillDemandResponse = {
      skills: [
        {
          skillId: "s-custom",
          standardName: "CustomLang",
          demandIndex: 0,
          recentDemandRate: 0,
          baselineDemandRate: 0,
          sources: [],
          weight: 0,
          status: "CUSTOM_SKILL_NO_STATS",
        },
        {
          skillId: "s-rare",
          standardName: "RareTech",
          demandIndex: 0,
          recentDemandRate: 0,
          baselineDemandRate: 0,
          sources: [],
          weight: 0,
          status: "INSUFFICIENT_SAMPLE",
        },
        {
          skillId: "s-clamped",
          standardName: "HotTrend",
          demandIndex: 400.0,
          recentDemandRate: 0.5,
          baselineDemandRate: 0.05,
          sources: [],
          weight: 1.0,
          status: "OK",
        },
      ],
      coverage: "보유 스킬 3개 중 1개 반영 (가중치 100% 재정규화)",
    };

    render(<SkillDemandSection data={mockData} />);

    expect(screen.getByText("통계 미제공")).toBeInTheDocument();
    expect(screen.getByText("표본 부족")).toBeInTheDocument();
    expect(screen.getByText("이상치 보정")).toBeInTheDocument();
  });

  it("원천 통계 토글 클릭 시 아코디언이 열리고 닫힌다", () => {
    const mockData: SkillDemandResponse = {
      skills: [
        {
          skillId: "s-1",
          standardName: "Spring Boot",
          demandIndex: 120.0,
          recentDemandRate: 0.15,
          baselineDemandRate: 0.12,
          sources: [{ source: "JOBKOREA", demandIndex: 118.0, sampleSize: 80 }],
          weight: 1.0,
          status: "OK",
        },
      ],
      coverage: "",
    };

    render(<SkillDemandSection data={mockData} />);

    const toggleBtn = screen.getByRole("button", { name: /원천 통계 보기/i });
    fireEvent.click(toggleBtn);

    expect(screen.getByText(/잡코리아/)).toBeInTheDocument();
    expect(screen.getByText(/80건/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /접기/i }));
    expect(screen.queryByText(/80건/)).not.toBeInTheDocument();
  });

  it("등록된 스킬이 없을 때 빈 안내 문구를 노출한다", () => {
    const mockData: SkillDemandResponse = {
      skills: [],
      coverage: "",
    };

    render(<SkillDemandSection data={mockData} />);
    expect(
      screen.getByText("등록된 보유 스킬이 없습니다."),
    ).toBeInTheDocument();
  });
});
