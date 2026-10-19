import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { NegotiationTargets } from "../../lib/api/dashboard";
import { NegotiationTargetsBlock } from "./NegotiationTargetsBlock";

const TARGETS: NegotiationTargets = {
  toWageStatMedian: { raiseRatePercent: 20, status: "BELOW" },
  toPeerP75: { raiseRatePercent: null, status: "ABOVE" },
  toPurchasingPowerRecovery: {
    raiseRatePercent: 4.3,
    status: "BELOW",
    sinceYearMonth: "202503",
  },
};

describe("NegotiationTargetsBlock (US-D7)", () => {
  it("NEGOTIATE 는 협상 목표 인상률 제목과 세 줄을 보인다", () => {
    render(
      <NegotiationTargetsBlock
        signalCode="NEGOTIATE"
        wageBasis="AGE_BAND_PROXY"
        targets={TARGETS}
      />,
    );

    expect(screen.getByText("협상 목표 인상률")).toBeInTheDocument();
    expect(screen.getByText("임금통계 중앙값까지")).toBeInTheDocument();
    expect(screen.getByText("+20.0%")).toBeInTheDocument();
    expect(screen.getByText("동료집단 상위 25%까지")).toBeInTheDocument();
    expect(screen.getByText("이미 상회")).toBeInTheDocument();
    expect(screen.getByText("2025년 3월 이후 물가 상승분")).toBeInTheDocument();
    expect(screen.getByText("+4.3%")).toBeInTheDocument();
    expect(screen.getByText("연령대 기준 추정 · KOSIS")).toBeInTheDocument();
  });

  it("SEEK_JOB 은 희망 연봉 목표 제목과 '수준' 라벨이다", () => {
    render(<NegotiationTargetsBlock signalCode="SEEK_JOB" targets={TARGETS} />);

    expect(screen.getByText("희망 연봉 목표")).toBeInTheDocument();
    expect(screen.getByText("임금통계 중앙값 수준")).toBeInTheDocument();
    expect(screen.getByText("동료집단 상위 25% 수준")).toBeInTheDocument();
    expect(
      screen.getByText("2025년 3월 이후 물가 상승분 반영"),
    ).toBeInTheDocument();
    expect(screen.getByText("KOSIS")).toBeInTheDocument();
  });

  it("복구선 NONE 은 물가 상승분 없음이다", () => {
    render(
      <NegotiationTargetsBlock
        signalCode="NEGOTIATE"
        targets={{
          ...TARGETS,
          toPurchasingPowerRecovery: {
            raiseRatePercent: null,
            status: "NONE",
            sinceYearMonth: "202509",
          },
        }}
      />,
    );

    expect(screen.getByText("물가 상승분 없음")).toBeInTheDocument();
  });

  it("null 줄은 렌더하지 않는다", () => {
    render(
      <NegotiationTargetsBlock
        signalCode="NEGOTIATE"
        targets={{ ...TARGETS, toPeerP75: null }}
      />,
    );

    expect(screen.queryByText(/동료집단 상위 25%/)).not.toBeInTheDocument();
    expect(screen.getByText("임금통계 중앙값까지")).toBeInTheDocument();
  });

  it("세 줄 모두 null 이면 아무것도 렌더하지 않는다", () => {
    const { container } = render(
      <NegotiationTargetsBlock
        signalCode="NEGOTIATE"
        targets={{
          toWageStatMedian: null,
          toPeerP75: null,
          toPurchasingPowerRecovery: null,
        }}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("targets 가 없으면 렌더하지 않는다", () => {
    const { container } = render(
      <NegotiationTargetsBlock signalCode="NEGOTIATE" targets={null} />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("STAY 는 렌더하지 않는다", () => {
    const { container } = render(
      <NegotiationTargetsBlock signalCode="STAY" targets={TARGETS} />,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
