import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { SkillRecommendationsResponse } from "../../lib/skillApi";
import { SkillRecommendationsSection } from "./SkillRecommendationsSection";

describe("SkillRecommendationsSection", () => {
  it("동료집단 비교 기준과 추천 스킬 목록(수요율·추세·동시출현 근거)을 렌더링한다", () => {
    const mockData: SkillRecommendationsResponse = {
      peerGroup: {
        appliedConditions: "서울 전체 / 경력 MID / 회사규모 무관",
      },
      recommendations: [
        {
          standardName: "Kubernetes",
          category: "DEVOPS",
          demandRate: 0.245,
          growthTrend: "UP",
          coOccurrence: { withSkill: "Spring Boot", rate: 0.62 },
          reason: "보유하신 Spring Boot 공고의 62%가 함께 요구합니다.",
        },
        {
          standardName: "TypeScript",
          category: "FRONTEND",
          demandRate: 0.182,
          growthTrend: "STABLE",
          coOccurrence: null,
          reason: "동료집단 내 공고의 18.2%에서 요구되는 핵심 스택입니다.",
        },
        {
          standardName: "Jenkins",
          category: "DEVOPS",
          demandRate: 0.095,
          growthTrend: "DOWN",
          coOccurrence: null,
          reason: "동료집단 내 공고의 9.5%에서 요구되는 스택입니다.",
        },
      ],
    };

    render(<SkillRecommendationsSection data={mockData} />);

    expect(screen.getByTestId("skill-recommendations-section")).toHaveAttribute(
      "data-slot",
      "card",
    );
    expect(
      screen.getByText("비교 기준: 서울 전체 / 경력 미들 / 회사규모 무관"),
    ).toBeInTheDocument();

    const list = screen.getByRole("list", {
      name: "동료집단 추천 스킬 목록",
    });
    expect(within(list).getAllByRole("listitem")).toHaveLength(3);

    expect(screen.getByText("Kubernetes")).toBeInTheDocument();
    expect(screen.getByText("수요율 24.5%")).toBeInTheDocument();
    expect(screen.getByText("▲ 상승")).toBeInTheDocument();
    expect(
      screen.getByText("보유하신 Spring Boot 공고의 62%가 함께 요구합니다."),
    ).toBeInTheDocument();

    expect(screen.getByText("TypeScript")).toBeInTheDocument();
    expect(screen.getByText("수요율 18.2%")).toBeInTheDocument();
    expect(screen.getByText("- 유지")).toBeInTheDocument();

    expect(screen.getByText("Jenkins")).toBeInTheDocument();
    expect(screen.getByText("▼ 하락")).toBeInTheDocument();
  });
});
