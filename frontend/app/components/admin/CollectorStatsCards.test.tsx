import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { CollectorStatsCards } from "./CollectorStatsCards";

const stats = {
  totalRawIngest: 100,
  totalJobPostings: 16104,
  totalSkills: 700,
  totalWageStats: 288,
  postingsBySource: { JOBKOREA: 7145, SARAMIN: 2862, WANTED: 6097 },
};

describe("CollectorStatsCards 원천 분포", () => {
  it("서버가 준 원천을 모두 합산하고 원티드도 보인다", () => {
    render(<CollectorStatsCards stats={stats} isLoading={false} />);
    expect(screen.getByText("총 16,104건")).toBeInTheDocument();
    expect(screen.getByText("잡코리아 7,145건 (44%)")).toBeInTheDocument();
    expect(screen.getByText("원티드 6,097건 (38%)")).toBeInTheDocument();
    expect(screen.getByText("사람인 2,862건 (18%)")).toBeInTheDocument();
  });

  it("누적 기준과 점핏이 사람인에 포함됨을 밝힌다", () => {
    render(<CollectorStatsCards stats={stats} isLoading={false} />);
    expect(
      screen.getByText(/누적 공고 수 · 점핏 공고는 사람인에 포함/),
    ).toBeInTheDocument();
  });

  it("모르는 원천도 코드 그대로 보이고 0건 원천은 그리지 않는다", () => {
    render(
      <CollectorStatsCards
        stats={{
          ...stats,
          postingsBySource: { JOBKOREA: 1, NEWSRC: 1, SARAMIN: 0 },
        }}
        isLoading={false}
      />,
    );
    expect(screen.getByText("NEWSRC 1건 (50%)")).toBeInTheDocument();
    expect(screen.queryByText(/사람인 0건/)).not.toBeInTheDocument();
  });

  it("처음 불러오는 동안은 LoadingState 다", () => {
    render(<CollectorStatsCards stats={null} isLoading />);
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(document.querySelector(".animate-pulse")).toBeNull();
  });

  it("원천 막대는 carix 토큰 색이다", () => {
    const { container } = render(
      <CollectorStatsCards stats={stats} isLoading={false} />,
    );
    const bar = container.querySelector('[title^="잡코리아"]');
    expect(bar?.className).toMatch(/bg-carix-/);
  });
});
