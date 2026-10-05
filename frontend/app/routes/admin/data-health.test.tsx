import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DataHealthPage from "./data-health";
import * as api from "../../lib/collectorApi";

vi.mock("../../lib/collectorApi", async (orig) => ({
  ...(await orig<typeof import("../../lib/collectorApi")>()),
  fetchDataHealth: vi.fn(),
  getAdminKey: vi.fn(() => "k"),
}));

const report: api.DataHealthReport = {
  evaluatedAt: "2026-10-05T18:00:00+09:00",
  referenceMonth: "202610",
  previousMonth: "202609",
  engineRuleVersion: "1.3.0",
  summary: {
    users: 3,
    fullyAnswered: 1,
    gaps: [{ answer: "MVI", reason: "INSUFFICIENT_SAMPLE", users: 1 }],
    verdictCounts: { ERROR: 1, WARN: 0, OK: 1, UNKNOWN: 1 },
  },
  groups: [
    {
      id: "PPI",
      title: "구매력 지수",
      question: "내 실질 구매력은?",
      checks: [
        {
          id: "CPI_LATEST",
          title: "CPI 최신월",
          verdict: "OK",
          message: "최신 CPI 202609 (1개월 지연)",
          impactUsers: 0,
          action: null,
          actionLink: null,
          drillDown: null,
          evidence: {},
        },
        {
          id: "CPI_GAP",
          title: "CPI 중간 결측",
          verdict: "ERROR",
          message: "결측 달 1개",
          impactUsers: 2,
          action: "결측 월 CPI 재적재",
          actionLink: "/admin/collector",
          drillDown: "/admin/data?tab=cpi",
          evidence: { incompleteMonths: ["202403"] },
        },
        {
          id: "X",
          title: "엔진",
          verdict: "UNKNOWN",
          message: "엔진 규칙 버전을 받지 못했습니다",
          impactUsers: null,
          action: null,
          actionLink: null,
          drillDown: null,
          evidence: {},
        },
      ],
    },
  ],
};

function renderPage() {
  const Stub = createRoutesStub([
    { path: "/admin/data-health", Component: DataHealthPage },
  ]);
  render(<Stub initialEntries={["/admin/data-health"]} />);
}

describe("DataHealthPage", () => {
  beforeEach(() => {
    vi.mocked(api.fetchDataHealth).mockResolvedValue(report);
  });

  it("원본 링크가 있는 점검은 「원본 보기」로 간다", async () => {
    renderPage();
    expect(
      await screen.findByRole("link", { name: "CPI 중간 결측 원본 보기" }),
    ).toHaveAttribute("href", "/admin/data?tab=cpi");
  });

  it("세 질문에 온전히 답하는 사용자 수를 보인다", async () => {
    renderPage();
    expect(await screen.findByText("1 / 3명")).toBeInTheDocument();
  });

  it("문제를 정상보다 먼저 보이고 정상 줄은 접는다", async () => {
    renderPage();
    const rows = await screen.findAllByRole("listitem");
    expect(rows[0]).toHaveTextContent("CPI 중간 결측");
    expect(
      screen.queryByText("최신 CPI 202609 (1개월 지연)"),
    ).not.toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: /정상 1건 보기/ }),
    );
    expect(
      screen.getByText("최신 CPI 202609 (1개월 지연)"),
    ).toBeInTheDocument();
  });

  it("판정 불가는 정상으로 보이지 않는다", async () => {
    renderPage();
    expect(await screen.findByText("판정 불가")).toBeInTheDocument();
  });

  it("조치 링크는 수집 파이프라인으로 간다", async () => {
    renderPage();
    expect(
      await screen.findByRole("link", { name: "결측 월 CPI 재적재" }),
    ).toHaveAttribute("href", "/admin/collector");
  });

  it("불러오지 못하면 재시도를 보인다", async () => {
    vi.mocked(api.fetchDataHealth).mockRejectedValue({
      code: "X",
      message: "x",
      status: 500,
    });
    renderPage();
    expect(
      await screen.findByText("상태를 불러오지 못했습니다"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });

  it("다시 점검이 실패하면 이전 결과를 두고 알린다", async () => {
    renderPage();
    expect(await screen.findByText("1 / 3명")).toBeInTheDocument();
    vi.mocked(api.fetchDataHealth).mockRejectedValue({
      code: "X",
      message: "x",
      status: 500,
    });
    await userEvent.click(screen.getByRole("button", { name: "다시 점검" }));
    expect(
      await screen.findByText(
        "상태를 불러오지 못했습니다 — 아래는 이전 점검 결과입니다",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("1 / 3명")).toBeInTheDocument();
  });

  it("403 이면 관리자 키 모달만 열고 실패 알림은 띄우지 않는다", async () => {
    vi.mocked(api.fetchDataHealth).mockRejectedValue({
      code: "FORBIDDEN",
      message: "x",
      status: 403,
    });
    renderPage();
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(
      screen.queryByText(/상태를 불러오지 못했습니다/),
    ).not.toBeInTheDocument();
  });

  it("헤더의 관리자 키 버튼이 키 모달을 연다", async () => {
    renderPage();
    expect(await screen.findByText("1 / 3명")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "관리자 키" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
