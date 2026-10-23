import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { SavedScenario } from "../../lib/api/scenario";
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
      typeLabel: "추정 추가보상",
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
  it("엔진 1 항목은 다시 계산하지 않고 저장 결과에 이전 방식 표식을 붙인다", async () => {
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

    expect(screen.getByText("이전 방식")).toBeInTheDocument();
    expect(screen.getByText("차이 +237만")).toBeInTheDocument();
    expect(scenarioApi.evaluate).not.toHaveBeenCalled();
    expect(onLoad).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "다시 계산" }));

    expect(onRecalculate).toHaveBeenCalledWith(
      legacyToRequest(legacy.legacy!),
      "예전 오퍼 · 2026-05-01",
    );
  });
});
