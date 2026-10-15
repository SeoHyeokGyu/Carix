import { expectHeadingOutline } from "../../test-utils/headingOutline";
import { describe, expect, it, vi, beforeEach } from "vitest";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import DashboardHistory from "./history";
import * as dashboardMarketApi from "../../lib/dashboardMarketApi";
import { UnauthorizedError } from "../../lib/coreApi";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock("../../lib/dashboardMarketApi", () => ({
  fetchSnapshotHistory: vi.fn(),
}));

type Legacy = Omit<dashboardMarketApi.SnapshotPoint, "recorded" | "changed">;

/** 기존 픽스처: 당시 판정을 현재 값과 같게 두어 기존 단언이 그대로 성립하게 한다. */
function withRecorded(points: Legacy[]): dashboardMarketApi.SnapshotPoint[] {
  return points.map((p) => ({
    ...p,
    recorded: {
      purchasingPowerIndex: p.purchasingPowerIndex,
      marketValueIndex: p.marketValueIndex,
      openingWindowIndex: p.openingWindowIndex,
      decisionSignal: p.decisionSignal,
      label: p.label,
      dataVersion: p.dataVersion,
      ruleVersion: p.ruleVersion,
      rationale: p.rationale,
      recordedAt: "2026-09-15T03:00:00Z",
    },
    changed: false,
  }));
}

const rationale = {
  rulesApplied: ["R1"],
  excluded: [],
  baseDate: "2026-09-01",
};
const current = (month: string, signal: string, label: string): Legacy => ({
  baseYearMonth: month,
  purchasingPowerIndex: 101,
  marketValueIndex: 110,
  openingWindowIndex: null,
  decisionSignal: signal,
  label,
  dataVersion: "20261009000000-000",
  ruleVersion: "1.5.0",
  rationale,
});
const recorded = (signal: string, label: string, mvi = 100) => ({
  purchasingPowerIndex: 99,
  marketValueIndex: mvi,
  openingWindowIndex: null,
  decisionSignal: signal,
  label,
  dataVersion: "20260915000000-000",
  ruleVersion: "1.4.0",
  rationale,
  recordedAt: "2026-09-15T03:00:00Z",
});

function renderPage(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <DashboardHistory />
    </MemoryRouter>,
  );
}

describe("S-11 DashboardHistory 라우트", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("헤더 위에 판정 화면으로 돌아가는 링크를 둔다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [],
    });

    render(
      <MemoryRouter>
        <DashboardHistory />
      </MemoryRouter>,
    );

    expect(await screen.findByRole("link", { name: "판정" })).toHaveAttribute(
      "href",
      "/dashboard",
    );
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

    expect(screen.queryByText("S-11")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "지난 판정" }),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByTestId("empty-state")).toBeInTheDocument();
      expect(
        screen.getByText("기록된 월별 스냅샷이 없습니다."),
      ).toBeInTheDocument();
    });
    expectHeadingOutline();
  });

  it("스냅샷이 1개일 때 단건 안내 문구(다음 달부터 변화를 볼 수 있습니다)를 렌더링한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: withRecorded([
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
      ]),
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
    expectHeadingOutline();
  });

  // 대시보드는 지표 셋을 보인다 — 기록 화면도 같은 셋을 보여야 비교가 된다.
  it("개방창 지수 열을 보이고 값이 없는 달은 데이터 없음으로 표시한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: withRecorded([
        {
          baseYearMonth: "202607",
          purchasingPowerIndex: 94.2,
          marketValueIndex: 117.5,
          openingWindowIndex: 108.3,
          decisionSignal: "SEEK_JOB",
          label: "이직 탐색",
          dataVersion: "v1.1",
          ruleVersion: "v1.0",
          rationale: null,
        },
        {
          baseYearMonth: "202606",
          purchasingPowerIndex: 93.0,
          marketValueIndex: 116.0,
          openingWindowIndex: null,
          decisionSignal: "SEEK_JOB",
          label: "이직 탐색",
          dataVersion: "v1.0",
          ruleVersion: "v1.0",
          rationale: null,
        },
      ]),
    });

    render(
      <MemoryRouter>
        <DashboardHistory />
      </MemoryRouter>,
    );

    expect(
      await screen.findByRole("columnheader", { name: "개방창 지수" }),
    ).toBeInTheDocument();
    const [, july, june] = screen.getAllByRole("row");
    expect(within(july).getByText("108.3")).toBeInTheDocument();
    expect(within(june).getByText("데이터 없음")).toBeInTheDocument();
  });

  it("스냅샷 목록(2건 이상)과 결측치 뱃지, 상세 패널 펼침을 정상 동작한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: withRecorded([
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
      ]),
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
        screen.getAllByText("• RULE_2_SEEK_JOB_MVI_HIGH")[0],
      ).toBeInTheDocument();
      expect(screen.getAllByText("✕ React Native")[0]).toBeInTheDocument();
      expect(screen.getAllByText(/데이터 버전: v1.1/)[0]).toBeInTheDocument();
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
    expectHeadingOutline();
    expect(
      screen.getByRole("heading", { level: 2, name: /스냅샷 산출 근거/ }),
    ).toBeInTheDocument();
  });

  it("수집 공백이 원인인 달에는 입력 유도 대신 집계 안내를 띄운다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: withRecorded([
        {
          baseYearMonth: "202604",
          purchasingPowerIndex: 100,
          marketValueIndex: null,
          decisionSignal: "INSUFFICIENT_DATA",
          label: "데이터 부족",
          dataVersion: "v1.0",
          ruleVersion: "v1.0",
          rationale: {
            rulesApplied: ["시장 집계 미수집 구간"],
            excluded: ["Spring Boot"],
            baseDate: "2026-04-30",
          },
        },
        {
          baseYearMonth: "202603",
          purchasingPowerIndex: 100,
          marketValueIndex: 100,
          decisionSignal: "STAY",
          label: "현 상태 유지",
          dataVersion: "v1.0",
          ruleVersion: "v1.0",
          rationale: {
            rulesApplied: ["스킬 수요 안정"],
            excluded: [],
            baseDate: "2025-12-31",
          },
        },
      ]),
    });

    render(
      <MemoryRouter>
        <DashboardHistory />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("2026-04")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("2026-04"));

    await waitFor(() => {
      expect(
        screen.getByText(/시장 집계가 수집되지 않아 지수를 낼 수 없습니다/),
      ).toBeInTheDocument();
    });

    // 스킬이나 소득을 넣어도 해소되지 않는 구간이라 유도 링크를 띄우면 안 된다.
    expect(
      screen.queryByRole("link", { name: "소득 정보 입력하기" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "보유 스킬 입력하기" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/입력 데이터가 부족하여/),
    ).not.toBeInTheDocument();
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

    // 보여 줄 데이터가 없는 실패 — 빈 상태가 아니라 실패 EmptyState 하나다.
    expect(
      await screen.findByText("월별 스냅샷 이력을 불러오지 못했습니다"),
    ).toBeInTheDocument();
    expect(screen.queryByText("기록된 월별 스냅샷이 없습니다.")).toBeNull();

    const retryBtn = screen.getByRole("button", { name: "다시 시도" });
    fireEvent.click(retryBtn);

    expect(
      await screen.findByText("기록된 월별 스냅샷이 없습니다."),
    ).toBeInTheDocument();
    expect(dashboardMarketApi.fetchSnapshotHistory).toHaveBeenCalledTimes(2);
    expectHeadingOutline();
  });

  // 들어올 때 불러오고 실패하면 「다시 시도」가 있다 — 제목 줄 새로고침은 두지 않는다.
  it("제목 줄에 새로고침 버튼이 없고 h1 은 하나다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [],
    });

    render(
      <MemoryRouter>
        <DashboardHistory />
      </MemoryRouter>,
    );

    await screen.findByTestId("empty-state");
    expect(screen.queryByRole("button", { name: /새로고침/ })).toBeNull();
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("link", { name: "판정" })).toHaveAttribute(
      "href",
      "/dashboard",
    );
  });

  it("적용 규칙이 비어 있는 스냅샷 행 상세 패널에서는 '기록된 적용 규칙 없음'을 표시한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: withRecorded([
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
      ]),
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
      expect(
        screen.getAllByText("기록된 적용 규칙 없음")[0],
      ).toBeInTheDocument();
    });
  });

  // 세션 게이트가 켜진 뒤로 401 이 올 수 있다. 문자열 오류로 접으면 사용자는 다시 시도만
  // 누르게 되고 로그인으로 돌아갈 길이 없다.
  it("세션이 만료되면 로그인으로 보낸다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockRejectedValue(
      new UnauthorizedError(),
    );

    render(
      <MemoryRouter>
        <DashboardHistory />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/login", { replace: true });
    });
    expect(
      screen.queryByText("월별 스냅샷 이력을 불러오지 못했습니다"),
    ).not.toBeInTheDocument();
  });

  it("세션과 무관한 실패는 로그인으로 보내지 않고 오류 문구를 띄운다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockRejectedValue({
      code: "UNKNOWN",
      message: "서버 오류",
      status: 500,
    });

    render(
      <MemoryRouter>
        <DashboardHistory />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(
        screen.getByText("월별 스냅샷 이력을 불러오지 못했습니다"),
      ).toBeInTheDocument();
    });
    expect(mockNavigate).not.toHaveBeenCalled();
  });
  it("표는 당시 판정을 보이고 신호가 바뀌었으면 현재 규칙 칩을 단다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          ...current("202609", "SEEK_JOB", "이직 탐색"),
          recorded: recorded("STAY", "현 상태 유지"),
          changed: true,
        },
        {
          ...current("202608", "STAY", "현 상태 유지"),
          recorded: recorded("STAY", "현 상태 유지"),
          changed: false,
        },
      ],
    });
    renderPage("/dashboard/history");
    const row = (await screen.findByText("2026-09")).closest("tr")!;
    expect(within(row).getByText("100.0")).toBeInTheDocument();
    expect(
      within(row).getByText("지금 다시 계산하면: 이직 탐색"),
    ).toBeInTheDocument();
    const aug = screen.getByText("2026-08").closest("tr")!;
    expect(
      within(aug).queryByText(/지금 다시 계산하면:/),
    ).not.toBeInTheDocument();
  });

  it("보이는 달이 하나뿐이면 소급 계산 달이 숨어 있어도 단건 안내를 보인다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          ...current("202609", "STAY", "현 상태 유지"),
          recorded: recorded("STAY", "현 상태 유지"),
          changed: false,
        },
        {
          ...current("202201", "STAY", "현 상태 유지"),
          recorded: null,
          changed: false,
        },
      ],
    });
    renderPage("/dashboard/history");
    await screen.findByText("2026-09");
    expect(screen.getByTestId("history-single-notice")).toBeInTheDocument();
  });

  it("소급 계산한 달은 기본으로 숨기고 show=all 이면 배지와 함께 보인다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          ...current("202609", "STAY", "현 상태 유지"),
          recorded: recorded("STAY", "현 상태 유지"),
          changed: false,
        },
        {
          ...current("202201", "STAY", "현 상태 유지"),
          recorded: null,
          changed: false,
        },
      ],
    });
    renderPage("/dashboard/history");
    await screen.findByText("2026-09");
    expect(screen.queryByText("2022-01")).not.toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("checkbox", { name: "소급 계산한 달도 보기" }),
    );
    const row = (await screen.findByText("2022-01")).closest("tr")!;
    expect(within(row).getAllByText("소급 계산").length).toBeGreaterThan(0);
  });

  it("소급 계산한 달의 상세를 연 채 토글을 끄면 상세 패널도 닫는다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          ...current("202609", "STAY", "현 상태 유지"),
          recorded: recorded("STAY", "현 상태 유지"),
          changed: false,
        },
        {
          ...current("202201", "STAY", "현 상태 유지"),
          recorded: null,
          changed: false,
        },
      ],
    });
    renderPage("/dashboard/history?show=all");
    await userEvent.click(await screen.findByText("2022-01"));
    expect(screen.getByTestId("snapshot-detail-panel")).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("checkbox", { name: "소급 계산한 달도 보기" }),
    );
    await waitFor(() => {
      expect(screen.queryByText("2022-01")).not.toBeInTheDocument();
    });
    expect(
      screen.queryByTestId("snapshot-detail-panel"),
    ).not.toBeInTheDocument();
  });

  it("입력 유도는 당시가 아니라 현재 판정이 데이터 부족일 때만 띄운다", async () => {
    const insufficientRationale = {
      rulesApplied: ["RULE_1_DATA_MISSING"],
      excluded: [],
      baseDate: "2026-09-01",
    };
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          ...current("202609", "STAY", "현 상태 유지"),
          recorded: {
            ...recorded("INSUFFICIENT_DATA", "데이터 부족"),
            rationale: insufficientRationale,
          },
          changed: true,
        },
        {
          ...current("202608", "INSUFFICIENT_DATA", "데이터 부족"),
          rationale: insufficientRationale,
          recorded: recorded("STAY", "현 상태 유지"),
          changed: true,
        },
      ],
    });
    renderPage("/dashboard/history");

    // (a) 당시만 데이터 부족: 지금 입력해도 그 달은 안 바뀌고 현재 값은 정상이다.
    await userEvent.click(await screen.findByText("2026-09"));
    expect(screen.getByTestId("snapshot-detail-panel")).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "소득 정보 입력하기" }),
    ).not.toBeInTheDocument();

    // (b) 현재만 데이터 부족: 입력 보강으로 현재 재계산이 달라진다.
    await userEvent.click(screen.getByText("2026-08"));
    expect(
      await screen.findByRole("link", { name: "소득 정보 입력하기" }),
    ).toBeInTheDocument();
  });

  it("show=all 로 들어오면 소급 계산한 달이 바로 보인다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          ...current("202201", "STAY", "현 상태 유지"),
          recorded: null,
          changed: false,
        },
        {
          ...current("202609", "STAY", "현 상태 유지"),
          recorded: recorded("STAY", "현 상태 유지"),
          changed: false,
        },
      ],
    });
    renderPage("/dashboard/history?show=all");
    expect(await screen.findByText("2022-01")).toBeInTheDocument();
  });

  it("당시 판정이 있는 달이 하나도 없으면 전부 보이고 안내한다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          ...current("202202", "STAY", "현 상태 유지"),
          recorded: null,
          changed: false,
        },
        {
          ...current("202201", "STAY", "현 상태 유지"),
          recorded: null,
          changed: false,
        },
      ],
    });
    renderPage("/dashboard/history");
    expect(await screen.findByText("2022-02")).toBeInTheDocument();
    expect(
      screen.getByText(/아직 그 달에 기록된 판정이 없어/),
    ).toBeInTheDocument();
  });

  it("상세는 당시·현재 두 칸과 기록 시각(서울)·지수 차이를 보인다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          ...current("202609", "SEEK_JOB", "이직 탐색"),
          recorded: recorded("STAY", "현 상태 유지", 100),
          changed: true,
        },
        {
          ...current("202608", "STAY", "현 상태 유지"),
          recorded: recorded("STAY", "현 상태 유지"),
          changed: false,
        },
      ],
    });
    renderPage("/dashboard/history");
    await userEvent.click(await screen.findByText("2026-09"));
    const panel = screen.getByTestId("snapshot-detail-panel");
    expect(within(panel).getByText("당시")).toBeInTheDocument();
    expect(within(panel).getByText("현재 규칙")).toBeInTheDocument();
    expect(within(panel).getByText(/2026-09-15 12:00/)).toBeInTheDocument();
    expect(within(panel).getByText(/월 경계는 UTC 기준/)).toBeInTheDocument();
    expect(within(panel).getByText("+10.0")).toBeInTheDocument();
  });
  it("상세의 두 칸은 각자의 적용 규칙·제외 스킬·기준일·버전을 보인다", async () => {
    vi.mocked(dashboardMarketApi.fetchSnapshotHistory).mockResolvedValue({
      snapshots: [
        {
          ...current("202609", "SEEK_JOB", "이직 탐색"),
          rationale: {
            rulesApplied: ["RULE_NOW"],
            excluded: ["Skill Now"],
            baseDate: "2026-09-30",
          },
          recorded: {
            ...recorded("STAY", "현 상태 유지"),
            rationale: {
              rulesApplied: ["RULE_THEN"],
              excluded: [],
              baseDate: "2026-09-01",
            },
          },
          changed: true,
        },
      ],
    });
    renderPage("/dashboard/history");
    await userEvent.click(await screen.findByText("2026-09"));
    const then = within(screen.getByTestId("snapshot-column-recorded"));
    const now = within(screen.getByTestId("snapshot-column-current"));
    expect(then.getByText("• RULE_THEN")).toBeInTheDocument();
    expect(then.queryByText("• RULE_NOW")).not.toBeInTheDocument();
    expect(
      then.getByText("제외된 스킬 없음 (전체 반영됨)"),
    ).toBeInTheDocument();
    expect(then.getByText("기준일: 2026-09-01")).toBeInTheDocument();
    expect(then.getByText("규칙 버전: 1.4.0")).toBeInTheDocument();
    expect(
      then.getByText("데이터 버전: 20260915000000-000"),
    ).toBeInTheDocument();
    expect(now.getByText("• RULE_NOW")).toBeInTheDocument();
    expect(now.queryByText("• RULE_THEN")).not.toBeInTheDocument();
    expect(now.getByText("✕ Skill Now")).toBeInTheDocument();
    expect(now.getByText("기준일: 2026-09-30")).toBeInTheDocument();
    expect(now.getByText("규칙 버전: 1.5.0")).toBeInTheDocument();
    expect(
      now.getByText("데이터 버전: 20261009000000-000"),
    ).toBeInTheDocument();
  });
});
