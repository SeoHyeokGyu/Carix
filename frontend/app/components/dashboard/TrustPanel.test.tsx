import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { DashboardMetadata } from "../../lib/api/dashboard";
import { TrustPanel } from "./TrustPanel";

const METADATA: DashboardMetadata = {
  baseDate: "2026-08-01",
  updateDelayed: false,
  ruleVersion: "1.2.0",
  sampleSize: 308,
  salaryMissingRate: 69.81,
  wageStat: { statVintage: "2025" },
};

function renderPanel(
  overrides: Partial<Parameters<typeof TrustPanel>[0]> = {},
) {
  return render(
    <TrustPanel
      metadata={METADATA}
      rationale="공식 임금통계 중앙값과 비슷한 수준입니다."
      peerConditions={{
        text: "전국 / 4~7년 / 회사규모 무관 / 고용형태 무관",
        widened: false,
      }}
      {...overrides}
    />,
  );
}

describe("TrustPanel", () => {
  it("h2 섹션 제목 아래 수집 표본·비교 조건·판정 근거·규칙 버전을 보인다", () => {
    renderPanel();
    expect(
      screen.getByRole("heading", {
        level: 2,
        name: "이 판정은 이렇게 나왔습니다",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("308건")).toBeInTheDocument();
    expect(screen.getByText("93건 · 30%")).toBeInTheDocument();
    expect(
      screen.getByText("전국 / 4~7년 / 회사규모 무관 / 고용형태 무관"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("공식 임금통계 중앙값과 비슷한 수준입니다."),
    ).toBeInTheDocument();
    expect(screen.getByText(/1\.2\.0/)).toBeInTheDocument();
    expect(screen.getByText(/2025/)).toBeInTheDocument();
  });

  it("비교 조건에 원시 enum 이 나오지 않는다", () => {
    renderPanel();
    expect(screen.queryByText(/LARGE|FULL_TIME|MID/)).not.toBeInTheDocument();
  });

  it("완화가 걸렸으면 서버 문자열과 안내를 보인다", () => {
    renderPanel({
      peerConditions: {
        text: "서울 전체 / MID (4~7년) / 회사규모 무관 / FULL_TIME",
        widened: true,
      },
    });
    expect(
      screen.getByText("서울 전체 / MID (4~7년) / 회사규모 무관 / FULL_TIME"),
    ).toBeInTheDocument();
    expect(screen.getByText("조건을 넓혀 비교했습니다")).toBeInTheDocument();
  });
});
