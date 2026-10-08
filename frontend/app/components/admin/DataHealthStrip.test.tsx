import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DataHealthStrip } from "./DataHealthStrip";
import * as api from "../../lib/collectorApi";

vi.mock("../../lib/collectorApi", async (orig) => ({
  ...(await orig<typeof import("../../lib/collectorApi")>()),
  fetchDataHealth: vi.fn(),
}));

const IDS = [
  ["AGGREGATE_STALE", "집계 최신성"],
  ["RULE_VERSION", "규칙 버전"],
  ["SNAPSHOT_FRESHNESS", "스냅샷 신선도"],
  ["CPI_LATEST", "CPI 최신월"],
  ["CRAWL_FRESHNESS", "수집 신선도"],
] as const;

function report(
  overrides: Record<string, Partial<api.HealthCheck>> = {},
  gaps: NonNullable<api.DataHealthReport["summary"]>["gaps"] = [],
): api.DataHealthReport {
  return {
    evaluatedAt: "2026-10-08T00:25:00Z",
    referenceMonth: "202610",
    previousMonth: "202609",
    engineRuleVersion: "1.3.0",
    summary: { users: 3, fullyAnswered: 3, gaps },
    checks: IDS.map(([id, title]) => ({
      id,
      title,
      verdict: "OK",
      message: `${title} 메시지`,
      impactUsers: null,
      action: null,
      actionLink: null,
      evidence: {},
      drillDown: null,
      ...overrides[id],
    })),
  };
}

function renderStrip(onAuthError = vi.fn(), refreshKey = 0) {
  const Stub = createRoutesStub([
    {
      path: "/admin/data",
      Component: () => (
        <DataHealthStrip onAuthError={onAuthError} refreshKey={refreshKey} />
      ),
    },
  ]);
  const view = render(<Stub initialEntries={["/admin/data"]} />);
  return view;
}

describe("DataHealthStrip", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("모두 OK 면 한 줄로 접힌다", async () => {
    vi.mocked(api.fetchDataHealth).mockResolvedValue(report());
    renderStrip();
    expect(await screen.findByText("점검 정상")).toBeInTheDocument();
    expect(screen.getByText("답하는 사용자 3/3")).toBeInTheDocument();
    expect(screen.getByText(/기준 202610/)).toBeInTheDocument();
    expect(screen.queryByText("CPI 최신월 메시지")).not.toBeInTheDocument();
  });

  it("경고 점검만 펼치고 나머지는 외 N개 정상으로 묶는다", async () => {
    vi.mocked(api.fetchDataHealth).mockResolvedValue(
      report({
        CPI_LATEST: {
          verdict: "WARN",
          message: "CPI 가 한 달 늦다",
          action: "수집 확인",
          actionLink: "/admin/collector",
          drillDown: "/admin/data?tab=cpi",
        },
      }),
    );
    renderStrip();
    expect(await screen.findByText("CPI 최신월")).toBeInTheDocument();
    expect(screen.getByText("CPI 가 한 달 늦다")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "CPI 최신월 수집 확인" }),
    ).toHaveAttribute("href", "/admin/collector");
    expect(
      screen.getByRole("link", { name: "CPI 최신월 근거" }),
    ).toHaveAttribute("href", "/admin/data?tab=cpi");
    expect(screen.getByText("외 4개 정상")).toBeInTheDocument();
  });

  it("판정 불가는 회색 배지로 정상과 구분된다", async () => {
    vi.mocked(api.fetchDataHealth).mockResolvedValue(
      report({ RULE_VERSION: { verdict: "UNKNOWN" } }),
    );
    renderStrip();
    expect(await screen.findByText("판정 불가")).toBeInTheDocument();
    expect(screen.queryByText("점검 정상")).not.toBeInTheDocument();
  });

  it("빠진 사유를 문제 줄 아래에 보인다", async () => {
    vi.mocked(api.fetchDataHealth).mockResolvedValue(
      report({ CPI_LATEST: { verdict: "WARN" } }, [
        { answer: "PPI", reason: "NO_SNAPSHOT", users: 2 },
      ]),
    );
    renderStrip();
    expect(
      await screen.findByText(/빠진 사유: 구매력 스냅샷 없음 2명/),
    ).toBeInTheDocument();
  });

  it("요약을 낼 수 없으면 답하는 사용자 판정 불가를 보이고 숫자를 지어내지 않는다", async () => {
    vi.mocked(api.fetchDataHealth).mockResolvedValue({
      ...report(),
      summary: null,
    });
    renderStrip();
    expect(
      await screen.findByText("답하는 사용자 판정 불가"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/답하는 사용자 \d/)).not.toBeInTheDocument();
  });

  it("500 이면 실패 문구와 다시 점검 버튼을 보이고 누르면 다시 부른다", async () => {
    vi.mocked(api.fetchDataHealth).mockRejectedValueOnce({ status: 500 });
    renderStrip();
    expect(
      await screen.findByText("점검을 불러오지 못했습니다"),
    ).toBeInTheDocument();
    vi.mocked(api.fetchDataHealth).mockResolvedValue(report());
    await userEvent.click(screen.getByRole("button", { name: "다시 점검" }));
    expect(await screen.findByText("점검 정상")).toBeInTheDocument();
    expect(api.fetchDataHealth).toHaveBeenCalledTimes(2);
  });

  it("답이 빈 대상 코드를 한글로 보이고 모르는 코드는 그대로 둔다", async () => {
    vi.mocked(api.fetchDataHealth).mockResolvedValue(
      report({ CPI_LATEST: { verdict: "WARN" } }, [
        { answer: "MVI", reason: "INSUFFICIENT_SAMPLE", users: 1 },
        { answer: "DECISION", reason: "MVI_UNCERTAIN", users: 2 },
        { answer: "ALL", reason: "NO_SNAPSHOT", users: 3 },
        { answer: "XYZ", reason: "NOT_AVAILABLE", users: 4 },
      ]),
    );
    renderStrip();
    expect(
      await screen.findByText(
        /시장가치 표본 부족 1명 · 결정 신호 시장가치 불확실 2명 · 전체 스냅샷 없음 3명 · XYZ 산출 불가 4명/,
      ),
    ).toBeInTheDocument();
  });

  it("점검 중 표시는 status, 실패 문구는 alert 역할을 가진다", async () => {
    let resolve: (r: api.DataHealthReport) => void = () => {};
    vi.mocked(api.fetchDataHealth).mockReturnValueOnce(
      new Promise((r) => (resolve = r)),
    );
    renderStrip();
    expect(screen.getByRole("status")).toHaveTextContent("점검 중…");
    resolve(report());
    await screen.findByText("점검 정상");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    vi.mocked(api.fetchDataHealth).mockRejectedValueOnce({ status: 500 });
    await userEvent.click(screen.getByRole("button", { name: "다시 점검" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "점검을 불러오지 못했습니다",
    );
  });

  it("403 이면 onAuthError 를 부른다", async () => {
    vi.mocked(api.fetchDataHealth).mockRejectedValue({ status: 403 });
    const onAuthError = vi.fn();
    renderStrip(onAuthError);
    await vi.waitFor(() => expect(onAuthError).toHaveBeenCalled());
    expect(
      await screen.findByRole("button", { name: "다시 점검" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("점검 중…")).not.toBeInTheDocument();
  });

  it("다시 점검하는 동안 이전 보고서를 두고 점검 중 표시를 붙인다", async () => {
    vi.mocked(api.fetchDataHealth).mockResolvedValueOnce(report());
    renderStrip();
    await screen.findByText("점검 정상");
    let resolve: (r: api.DataHealthReport) => void = () => {};
    vi.mocked(api.fetchDataHealth).mockReturnValueOnce(
      new Promise((r) => (resolve = r)),
    );
    await userEvent.click(screen.getByRole("button", { name: "다시 점검" }));
    expect(screen.getByText("점검 중…")).toBeInTheDocument();
    expect(screen.getByText("점검 정상")).toBeInTheDocument();
    resolve(report());
    await vi.waitFor(() =>
      expect(screen.queryByText("점검 중…")).not.toBeInTheDocument(),
    );
  });
});
