import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as income from "../../lib/api/income";
import * as profile from "../../lib/api/profile";
import type { ScenarioContext } from "../../lib/api/scenario";
import * as scenario from "../../lib/api/scenario";
import { CurrentJobStep } from "./CurrentJobStep";

vi.mock("../../lib/api/scenario", () => ({ putDraft: vi.fn() }));
vi.mock("../../lib/api/income", () => ({
  updateBonusTerms: vi.fn(),
  createBonus: vi.fn(),
}));
vi.mock("../../lib/api/profile", () => ({ patchProfile: vi.fn() }));

const context: ScenarioContext = {
  currentAnnualGross: 60_000_000,
  monthlyNet: null,
  hireDate: "2022-03-01",
  bonusIncluded: true,
  fixedBonusAnnual: null,
  defaultRaisePercent: 3,
  performanceBonusRange: null,
  inflation: {
    governmentPercent: 2,
    personalPercent: 2.4,
    gapPp: 0.4,
    topGapCategory: null,
    similar: false,
    asOf: "2026-09",
    source: "한국은행",
  },
  marketGapPercent: null,
};

const SUBMIT = "남으면 어떻게 되는지 보기";

function renderStep(onDone = vi.fn()) {
  render(
    <CurrentJobStep
      context={context}
      latestIncomeId="i-1"
      draft={null}
      onDone={onDone}
    />,
  );
  return onDone;
}

describe("CurrentJobStep", () => {
  beforeEach(() => vi.clearAllMocks());

  it("이번만 바꾸기는 소득 저장 API 를 부르지 않고 초안에만 담는다", async () => {
    vi.mocked(scenario.putDraft).mockResolvedValue(null);
    const onDone = renderStep();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "이번만 바꾸기" }));
    await user.type(screen.getByLabelText(/이번만 적용할 세전 연봉/), "7000");
    await user.click(screen.getByRole("button", { name: SUBMIT }));

    expect(scenario.putDraft).toHaveBeenCalledWith(
      expect.objectContaining({
        stay: expect.objectContaining({ currentGrossOverride: 70_000_000 }),
      }),
    );
    expect(income.updateBonusTerms).not.toHaveBeenCalled();
    expect(income.createBonus).not.toHaveBeenCalled();
    expect(profile.patchProfile).not.toHaveBeenCalled();
    expect(onDone).toHaveBeenCalled();
  });

  it("저장 API 가 실패하면 오류를 화면에 보이고 다음 단계로 가지 않는다", async () => {
    vi.mocked(scenario.putDraft).mockRejectedValue({
      code: "SERVER",
      message: "초안을 저장하지 못했습니다.",
      status: 500,
    });
    const onDone = renderStep();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: SUBMIT }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "초안을 저장하지 못했습니다.",
    );
    expect(onDone).not.toHaveBeenCalled();
  });
});
