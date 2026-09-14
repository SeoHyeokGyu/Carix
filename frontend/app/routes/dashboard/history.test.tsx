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
          label: "현 상태 유지",
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
          label: "이직 탐색",
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
          label: "데이터 부족",
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

  it("API 호출 실패 시 에러 알림을 표시하고 '다시 시도' 클릭 시 재호출한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory)
      .mockRejectedValueOnce(new Error("서버 에러 발생"))
      .mockResolvedValueOnce({ snapshots: [] });

    render(
      <MemoryRouter>
        <DashboardHistory />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
      expect(
        screen.getByText("월별 스냅샷 이력을 불러오는 데 실패했습니다."),
      ).toBeInTheDocument();
    });

    const retryBtn = screen.getByRole("button", { name: "다시 시도" });
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByTestId("history-empty-state")).toBeInTheDocument();
    });
    expect(dashboardMarketApi.fetchSnapshotHistory).toHaveBeenCalledTimes(2);
  });

  it("새로고침 버튼 클릭 시 최신 스냅샷 데이터를 다시 조회한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [],
    });

    render(
      <MemoryRouter>
        <DashboardHistory />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("history-empty-state")).toBeInTheDocument();
    });

    const refreshBtn = screen.getByRole("button", { name: "새로고침 ↻" });
    fireEvent.click(refreshBtn);

    await waitFor(() => {
      expect(dashboardMarketApi.fetchSnapshotHistory).toHaveBeenCalledTimes(2);
    });
  });

  it("적용 규칙이 비어 있는 스냅샷 행 상세 패널에서는 '기록된 적용 규칙 없음'을 표시한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          baseYearMonth: "202607",
          purchasingPowerIndex: 100.0,
          marketValueIndex: 100.0,
          decisionSignal: "STAY",
          label: "현 상태 유지",
          dataVersion: "v1.0",
          ruleVersion: "v1.0",
          rationale: {
            rulesApplied: [],
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
      expect(screen.getByText("2026-07")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("2026-07"));

    await waitFor(() => {
      expect(screen.getByText("기록된 적용 규칙 없음")).toBeInTheDocument();
    });
  });
});
