import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { DataHealthReport, HealthCheck } from "../../lib/collectorApi";
import { DataHealthSummary } from "./DataHealthSummary";

function check(id: string): HealthCheck {
  return {
    id,
    title: "구매력 지수",
    verdict: "UNKNOWN",
    message: "데이터를 읽지 못했습니다",
    impactUsers: null,
    action: null,
    actionLink: null,
    drillDown: null,
    evidence: {},
  };
}

function reportWith(checks: HealthCheck[]): DataHealthReport {
  return {
    evaluatedAt: "2026-10-05T18:00:00+09:00",
    referenceMonth: "202610",
    previousMonth: "202609",
    engineRuleVersion: null,
    summary: {
      users: 0,
      fullyAnswered: 0,
      gaps: [],
      verdictCounts: { ERROR: 0, WARN: 0, OK: 0, UNKNOWN: checks.length },
    },
    groups: [
      {
        id: "PPI",
        title: "구매력 지수",
        question: "내 실질 구매력은?",
        checks,
      },
    ],
  };
}

describe("DataHealthSummary", () => {
  it("점검 실패 줄이 있으면 0 / 0명 대신 판정 불가를 보인다", () => {
    render(<DataHealthSummary report={reportWith([check("PPI_FAILED")])} />);
    const summary = screen.getByRole("region", { name: "요약" });
    expect(within(summary).getByText("판정 불가")).toBeInTheDocument();
    expect(within(summary).queryByText("0 / 0명")).not.toBeInTheDocument();
  });

  it("점검 실패 줄이 없으면 답하는 사용자 수를 보인다", () => {
    render(<DataHealthSummary report={reportWith([check("CPI_LATEST")])} />);
    expect(screen.getByText("0 / 0명")).toBeInTheDocument();
  });

  it("원티드 그룹의 점검 실패는 판정 불가로 치지 않는다", () => {
    const report = reportWith([check("CPI_LATEST")]);
    report.groups.push({
      id: "WANTED",
      title: "원티드",
      question: "도입 중",
      checks: [check("WANTED_FAILED")],
    });
    render(<DataHealthSummary report={report} />);
    expect(screen.getByText("0 / 0명")).toBeInTheDocument();
    expect(screen.queryByText("판정 불가")).not.toBeInTheDocument();
  });
});
