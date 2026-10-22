import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "../../lib/collectorApi";
import { DataHealthStrip } from "./DataHealthStrip";

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

  it("성공 뒤 실패하고 다시 점검하면 이전 보고서를 두고 점검 중을 붙인다", async () => {
    vi.mocked(api.fetchDataHealth).mockResolvedValueOnce(
      report({ CPI_LATEST: { verdict: "WARN", message: "CPI 가 늦다" } }),
    );
    renderStrip();
    await screen.findByText("CPI 가 늦다");

    vi.mocked(api.fetchDataHealth).mockRejectedValueOnce({ status: 500 });
    await userEvent.click(screen.getByRole("button", { name: "다시 점검" }));
    expect(
      await screen.findByText("점검을 불러오지 못했습니다"),
    ).toBeInTheDocument();

    let resolve: (r: api.DataHealthReport) => void = () => {};
    vi.mocked(api.fetchDataHealth).mockReturnValueOnce(
      new Promise((r) => (resolve = r)),
    );
    await userEvent.click(screen.getByRole("button", { name: "다시 점검" }));
    expect(screen.getByRole("status")).toHaveTextContent("점검 중…");
    expect(screen.getByText("CPI 가 늦다")).toBeInTheDocument();
    expect(
      screen.queryByText("점검을 불러오지 못했습니다"),
    ).not.toBeInTheDocument();
    resolve(report());
    expect(await screen.findByText("점검 정상")).toBeInTheDocument();
  });
});
