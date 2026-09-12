import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import DashboardHistory from "./history";
import * as dashboardMarketApi from "../../lib/dashboardMarketApi";

vi.mock("../../lib/dashboardMarketApi", () => ({
  fetchSnapshotHistory: vi.fn(),
}));

describe("S-11 DashboardHistory 라우트", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("스냅샷이 0개일 때 비어있음 안내 상태를 렌더링한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [],
    });

    render(
      <MemoryRouter>
        <DashboardHistory />
      </MemoryRouter>,
    );

    expect(screen.getByText("S-11")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "월별 스냅샷 이력" }),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByTestId("history-empty-state")).toBeInTheDocument();
      expect(
        screen.getByText("기록된 월별 스냅샷이 없습니다."),
      ).toBeInTheDocument();
    });
  });

  it("스냅샷이 1개일 때 단건 안내 문구(다음 달부터 변화를 볼 수 있습니다)를 렌더링한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          baseYearMonth: "202607",
          purchasingPowerIndex: 98.5,
          marketValueIndex: 110.2,
          decisionSignal: "STAY",
          dataVersion: "v1.0",
          ruleVersion: "v1.0",
          rationale: {
            rulesApplied: ["RULE_5_DEFAULT_STAY"],
            excluded: [],
            baseDate: "2026-07-01",
          },
        },
      ],
    });

    render(
      <MemoryRouter>
        <DashboardHistory />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("history-single-notice")).toBeInTheDocument();
      expect(screen.getByText("현 상태 유지")).toBeInTheDocument();
      expect(screen.getByText("2026-07")).toBeInTheDocument();
    });
  });

  it("스냅샷 목록(2건 이상)과 결측치 뱃지, 상세 패널 펼침을 정상 동작한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          baseYearMonth: "202607",
          purchasingPowerIndex: 94.2,
          marketValueIndex: 117.5,
          decisionSignal: "SEEK_JOB",
          dataVersion: "v1.1",
          ruleVersion: "v1.0",
          rationale: {
            rulesApplied: ["RULE_2_SEEK_JOB_MVI_HIGH"],
            excluded: ["React Native"],
            baseDate: "2026-07-01",
          },
        },
        {
          baseYearMonth: "202606",
          purchasingPowerIndex: null,
          marketValueIndex: null,
          decisionSignal: "INSUFFICIENT_DATA",
          dataVersion: "v1.0",
          ruleVersion: "v1.0",
          rationale: {
            rulesApplied: ["RULE_1_DATA_MISSING"],
            excluded: [],
            baseDate: "2026-06-01",
          },
        },
      ],
    });

    render(
      <MemoryRouter>
        <DashboardHistory />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("2026-07")).toBeInTheDocument();
      expect(screen.getByText("2026-06")).toBeInTheDocument();
      expect(screen.getByText("이직 탐색")).toBeInTheDocument();
      expect(screen.getByText("데이터 부족")).toBeInTheDocument();
      expect(screen.getAllByText("데이터 없음").length).toBeGreaterThanOrEqual(
        1,
      );
    });

    // 첫 번째 행 클릭하여 상세 패널 열기
    const row = screen.getByText("2026-07");
    fireEvent.click(row);

    await waitFor(() => {
      expect(screen.getByTestId("snapshot-detail-panel")).toBeInTheDocument();
      expect(
        screen.getByText("• RULE_2_SEEK_JOB_MVI_HIGH"),
      ).toBeInTheDocument();
      expect(screen.getByText("✕ React Native")).toBeInTheDocument();
      expect(screen.getByText(/데이터 버전: v1.1/)).toBeInTheDocument();
    });

    // 두 번째 행 클릭 (데이터 부족 행)
    const rowMissing = screen.getByText("2026-06");
    fireEvent.click(rowMissing);

    await waitFor(() => {
      expect(
        screen.getByText(
          /입력 데이터가 부족하여 지수를 정상 판정하지 못했습니다/,
        ),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: "소득 정보 입력하기" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: "보유 스킬 입력하기" }),
      ).toBeInTheDocument();
    });
  });
});
