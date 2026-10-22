import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { SavedScenario } from "../../lib/api/scenario";
import { SavedScenarioList } from "./SavedScenarioList";

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

describe("SavedScenarioList", () => {
  it("엔진 1 항목은 다시 계산하지 않고 저장 결과에 이전 방식 표식을 붙인다", () => {
    const onLoad = vi.fn();
    render(
      <SavedScenarioList
        scenarios={[legacy]}
        onLoad={onLoad}
        onRecalculate={vi.fn()}
      />,
    );

    expect(screen.getByText("이전 방식(세전 기준)")).toBeInTheDocument();
    expect(screen.getByText(/61,800,000원/)).toBeInTheDocument();
    expect(screen.getByText(/64,166,667원/)).toBeInTheDocument();
    expect(onLoad).not.toHaveBeenCalled();
  });
});
