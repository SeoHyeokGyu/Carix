import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";

import type {
  SavedScenario,
  ScenarioVerification,
} from "../../lib/api/scenario";
import * as scenarioApi from "../../lib/api/scenario";
import { legacyToRequest, SavedScenarioMenu } from "./SavedScenarioMenu";

vi.mock("../../lib/api/scenario", () => ({ evaluate: vi.fn() }));

const legacy: SavedScenario = {
  id: "old",
  name: "예전 오퍼",
  engineVersion: 1,
  calculatedAt: "2026-05-01T00:00:00Z",
  request: null,
  result: null,
  legacy: {
    inputs: {
      scenarioName: "예전 오퍼",
      currentGrossSalary: 60_000_000,
      stayExpectedRaiseRate: 3,
      moveTargetGrossSalary: 70_000_000,
      moveOneTimeCost: 0,
      expectedUnemploymentMonths: 1,
      isEligibleForUnemploymentBenefit: false,
    },
    result: {
      id: "old",
      scenarioName: "예전 오퍼",
      stayCompensation12m: 61_800_000,
      moveCompensation12m: 64_166_667,
      estimatedUnemploymentBenefit: 0,
      unemploymentBenefitNote: "",
      difference: 2_366_667,
      type: "ESTIMATED_GAIN",
      typeLabel: "옮기면 추정 이득",
      warningNote: "",
      feasibility: {
        status: "INSUFFICIENT_SAMPLE",
        sampleSize: 0,
        minRequired: null,
        feasibilityScore: null,
        targetPercentile: null,
        difficultyGrade: null,
        feedbackMessage: null,
      },
    },
  },
};

describe("SavedScenarioMenu", () => {
  const openWith = async (verification: ScenarioVerification) => {
    render(
      <MemoryRouter>
        <SavedScenarioMenu
          scenarios={[{ ...legacy, verification }]}
          onLoad={vi.fn()}
          onRecalculate={vi.fn()}
        />
      </MemoryRouter>,
    );
    await userEvent.click(
      screen.getByRole("button", { name: "저장된 시나리오 1" }),
    );
  };

  it("비교 결과는 가정과 실제를 부호와 함께 나란히 보인다", async () => {
    await openWith({
      status: "STAY_COMPARED",
      monthsLeft: null,
      expectedRaisePercent: 5,
      actualRaisePercent: -2,
      offerAnnualGross: null,
      actualAnnualGross: null,
    });

    expect(screen.getByText("예상 +5.0%, 실제 -2.0%")).toBeInTheDocument();
  });

  it("저장 후 소득이 없으면 소득 입력으로 안내한다", async () => {
    await openWith({
      status: "NO_INCOME_AFTER",
      monthsLeft: null,
      expectedRaisePercent: null,
      actualRaisePercent: null,
      offerAnnualGross: null,
      actualAnnualGross: null,
    });

    expect(
      screen.getByText("저장 후 소득 기록이 없어 비교할 수 없습니다"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "소득 입력" })).toHaveAttribute(
      "href",
      "/mypage/records",
    );
  });

  it("엔진 1 항목은 다시 계산하지 않고 저장 결과에 예전 계산 표식을 붙인다", async () => {
    const onLoad = vi.fn();
    const onRecalculate = vi.fn();
    render(
      <SavedScenarioMenu
        scenarios={[legacy]}
        onLoad={onLoad}
        onRecalculate={onRecalculate}
      />,
    );

    await userEvent.click(
      screen.getByRole("button", { name: "저장된 시나리오 1" }),
    );

    expect(screen.getByText("예전 계산")).toBeInTheDocument();
    expect(screen.getByText("세전 12개월 차이 +237만")).toBeInTheDocument();
    expect(scenarioApi.evaluate).not.toHaveBeenCalled();
    expect(onLoad).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "다시 계산" }));

    expect(onRecalculate).toHaveBeenCalledWith(
      legacyToRequest(legacy.legacy!),
      "예전 오퍼 · 2026-05-01",
    );
  });
});
