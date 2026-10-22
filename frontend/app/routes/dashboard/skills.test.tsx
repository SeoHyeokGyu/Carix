import { render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { UnauthorizedError } from "../../lib/coreApi";
import * as dashboardMarketApi from "../../lib/dashboardMarketApi";
import * as skillApi from "../../lib/skillApi";
import DashboardSkills from "./skills";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock("../../lib/dashboardMarketApi", () => ({
  fetchSkillDemands: vi.fn(),
}));

vi.mock("../../lib/skillApi", () => ({
  fetchSkillRecommendations: vi.fn(),
}));

function renderSkills() {
  render(
    <MemoryRouter initialEntries={["/dashboard/skills"]}>
      <Routes>
        <Route path="/dashboard/skills" element={<DashboardSkills />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("S-18 DashboardSkills (스킬 분석) 라우트", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(dashboardMarketApi.fetchSkillDemands).mockResolvedValue({
      skills: [
        {
          skillId: "s-1",
          standardName: "Kotlin",
          demandIndex: 115.0,
          recentDemandRate: 0.12,
          baselineDemandRate: 0.1,
          sources: [{ source: "SARAMIN", demandIndex: 115.0, sampleSize: 45 }],
          weight: 1.0,
          status: "OK",
        },
      ],
      coverage: "보유 스킬 1개 중 1개 반영 (가중치 100% 재정규화)",
    });

    vi.mocked(skillApi.fetchSkillRecommendations).mockResolvedValue({
      peerGroup: {
        appliedConditions: "서울 전체 / 경력 MID / 회사규모 무관",
      },
      recommendations: [
        {
          standardName: "Kubernetes",
          category: "DEVOPS",
          demandRate: 0.245,
          growthTrend: "UP",
          coOccurrence: { withSkill: "Kotlin", rate: 0.62 },
          reason: "보유하신 Kotlin 공고의 62%가 함께 요구합니다.",
        },
      ],
    });
  });

  it("보유 스킬 관리 링크와 보유 스킬 수요 및 동료집단 추천 스킬 섹션을 렌더링한다", async () => {
    renderSkills();

    expect(
      screen.getByRole("link", { name: "보유 스킬 관리" }),
    ).toHaveAttribute("href", "/mypage/records/skills");

    await waitFor(() => {
      expect(screen.getByTestId("skill-demand-section")).toBeInTheDocument();
      expect(
        screen.getByTestId("skill-recommendations-section"),
      ).toBeInTheDocument();
    });

    expect(screen.getByText("Kotlin")).toBeInTheDocument();
    expect(screen.getByText("Kubernetes")).toBeInTheDocument();
    expect(screen.getByText("수요율 24.5%")).toBeInTheDocument();
    expect(
      screen.getByText("보유하신 Kotlin 공고의 62%가 함께 요구합니다."),
    ).toBeInTheDocument();
  });

  it("세션이 만료되면(401) 로그인으로 보낸다", async () => {
    vi.mocked(dashboardMarketApi.fetchSkillDemands).mockRejectedValue(
      new UnauthorizedError(),
    );
    vi.mocked(skillApi.fetchSkillRecommendations).mockRejectedValue(
      new UnauthorizedError(),
    );

    renderSkills();

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/login", { replace: true });
    });
  });

  it("한 섹션 조회만 실패하면 성공한 섹션은 그대로 그리고 실패한 섹션 안에만 다시 시도 버튼을 둔다", async () => {
    vi.mocked(skillApi.fetchSkillRecommendations).mockRejectedValue(
      new Error("서버 오류"),
    );

    renderSkills();

    await waitFor(() => {
      expect(screen.getByTestId("skill-demand-section")).toBeInTheDocument();
    });
    expect(screen.getByText("Kotlin")).toBeInTheDocument();

    const recommendSection = screen.getByRole("region", {
      name: "동료집단 추천 스킬",
    });
    expect(
      within(recommendSection).getByText(
        "동료집단 추천 스킬 데이터를 불러오지 못했습니다",
      ),
    ).toBeInTheDocument();
    expect(
      within(recommendSection).getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("스킬 시장 데이터를 불러오지 못했습니다"),
    ).toBeNull();
  });
});
