import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

  it("완화가 아니면 안내를 붙이지 않는다", () => {
    renderPanel({
      peerConditions: {
        text: "서울 전체 / MID (4~7년) / LARGE / FULL_TIME",
        widened: false,
      },
    });
    expect(
      screen.queryByText("조건을 넓혀 비교했습니다"),
    ).not.toBeInTheDocument();
  });

  it("표본 수가 없으면 수집 줄이 없다", () => {
    renderPanel({ metadata: { ...METADATA, sampleSize: undefined } });
    expect(screen.queryByText("수집한 채용공고")).not.toBeInTheDocument();
  });

  it("누락률이 없으면 연봉 줄만 없다", () => {
    renderPanel({ metadata: { ...METADATA, salaryMissingRate: null } });
    expect(screen.getByText("308건")).toBeInTheDocument();
    expect(screen.queryByText("그중 연봉이 적힌 것")).not.toBeInTheDocument();
  });

  it("처음엔 접혀 있고 토글로 연다", async () => {
    const user = userEvent.setup();
    renderPanel();

    const toggle = screen.getByRole("button", { name: "근거 펼치기" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(toggle).toHaveAttribute("aria-controls", "trust-panel-body");
    expect(screen.getByText("308건")).not.toBeVisible();

    await user.click(toggle);

    expect(screen.getByRole("button", { name: "근거 접기" })).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    expect(screen.getByText("308건")).toBeVisible();
  });

  it("조건 완화 안내는 접혀 있어도 제목 줄에 보인다", () => {
    renderPanel({
      peerConditions: {
        text: "서울 전체 / MID (4~7년) / 회사규모 무관 / FULL_TIME",
        widened: true,
      },
    });
    expect(screen.getByText("조건을 넓혀 비교했습니다")).toBeVisible();
  });

  it("물가 기여도 기준월을 기준 줄에 보인다", () => {
    renderPanel({
      metadata: {
        ...METADATA,
        topPriceDriver: {
          category: "TRANSPORT",
          label: "교통 부담률 최대 상승",
          estimated: false,
          cpiMonth: "202609",
        },
      },
    });
    expect(screen.getByText(/물가 2026-09/)).toBeInTheDocument();
  });

  it("물가 기여도가 없으면 물가 조각이 빠진다", () => {
    renderPanel();
    expect(screen.queryByText(/물가 \d/)).not.toBeInTheDocument();
  });
});
