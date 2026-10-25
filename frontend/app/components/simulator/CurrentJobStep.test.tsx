import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { act, createRef } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as income from "../../lib/api/income";
import * as profile from "../../lib/api/profile";
import type { EvaluateRequest, ScenarioContext } from "../../lib/api/scenario";
import { CurrentJobStep } from "./CurrentJobStep";
import type { StepFormHandle } from "./useDebouncedEmit";

vi.mock("../../lib/api/income", () => ({
  updateBonusTerms: vi.fn(),
  createBonus: vi.fn(),
}));
vi.mock("../../lib/api/profile", () => ({ patchProfile: vi.fn() }));

type Stay = EvaluateRequest["stay"];

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

function renderStep() {
  const ref = createRef<StepFormHandle<Stay>>();
  const onChange = vi.fn();
  const onRecordSaved = vi.fn();
  const onRecordSaving = vi.fn();
  render(
    <CurrentJobStep
      ref={ref}
      context={context}
      latestIncomeId="i-1"
      initial={null}
      onChange={onChange}
      onRecordSaved={onRecordSaved}
      onRecordSaving={onRecordSaving}
    />,
  );
  return { ref, onChange, onRecordSaved, onRecordSaving };
}

describe("CurrentJobStep", () => {
  beforeEach(() => vi.clearAllMocks());

  it("이번만 바꾸기는 소득 저장 API 를 부르지 않고 계산 요청에만 담는다", async () => {
    const { ref, onChange } = renderStep();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "이번만 바꾸기" }));
    await user.type(screen.getByLabelText(/이번만 적용할 세전 연봉/), "7000");
    let flushed: Stay | null = null;
    act(() => {
      flushed = ref.current!.flush();
    });

    expect(flushed).toEqual(
      expect.objectContaining({ currentGrossOverride: 70_000_000 }),
    );
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ currentGrossOverride: 70_000_000 }),
      "edit",
    );
    expect(income.updateBonusTerms).not.toHaveBeenCalled();
    expect(income.createBonus).not.toHaveBeenCalled();
    expect(profile.patchProfile).not.toHaveBeenCalled();
  });

  it("상여 답을 고르면 기록에 저장되고 성공 뒤에만 재평가를 알린다", async () => {
    let resolve!: (value: never[]) => void;
    vi.mocked(income.updateBonusTerms).mockReturnValue(
      new Promise((r) => {
        resolve = r;
      }),
    );
    const { onRecordSaved, onRecordSaving } = renderStep();
    const user = userEvent.setup();

    await user.click(screen.getByRole("radio", { name: "따로 받음" }));
    expect(income.updateBonusTerms).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText(/연간 고정상여/), "600");
    await user.tab();

    expect(income.updateBonusTerms).toHaveBeenCalledWith({
      applyFromIncomeId: "i-1",
      bonusIncluded: false,
      fixedBonusAnnual: 6_000_000,
    });
    expect(onRecordSaving).toHaveBeenLastCalledWith(true);
    expect(onRecordSaved).not.toHaveBeenCalled();

    await act(async () => resolve([]));

    expect(onRecordSaved).toHaveBeenCalledTimes(1);
    expect(onRecordSaving).toHaveBeenLastCalledWith(false);
    expect(screen.getByText("✓ 소득 이력에 저장됨")).toBeInTheDocument();
  });

  it("기록 저장이 실패하면 재평가를 알리지 않고 다시 시도를 보인다", async () => {
    vi.mocked(income.updateBonusTerms)
      .mockRejectedValueOnce({ code: "SERVER", message: "x", status: 500 })
      .mockResolvedValueOnce([]);
    const { onRecordSaved, onRecordSaving } = renderStep();
    const user = userEvent.setup();

    await user.click(screen.getByRole("radio", { name: "따로 받음" }));
    await user.type(screen.getByLabelText(/연간 고정상여/), "600");
    await user.tab();

    expect(
      await screen.findByText("소득 이력에 저장하지 못했습니다."),
    ).toBeInTheDocument();
    expect(onRecordSaved).not.toHaveBeenCalled();
    expect(onRecordSaving).toHaveBeenLastCalledWith(false);

    await user.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("✓ 소득 이력에 저장됨")).toBeInTheDocument();
    expect(income.updateBonusTerms).toHaveBeenCalledTimes(2);
    expect(income.updateBonusTerms).toHaveBeenLastCalledWith({
      applyFromIncomeId: "i-1",
      bonusIncluded: false,
      fixedBonusAnnual: 6_000_000,
    });
    expect(onRecordSaved).toHaveBeenCalledTimes(1);
  });

  it("상여 답을 연달아 바꾸면 앞선 저장이 끝난 뒤에 다음 답을 보낸다", async () => {
    // 동시에 보내면 늦게 끝난 앞선 요청이 새 답을 덮을 수 있다.
    let resolveFirst!: (value: never[]) => void;
    vi.mocked(income.updateBonusTerms)
      .mockReturnValueOnce(
        new Promise((r) => {
          resolveFirst = r;
        }),
      )
      .mockResolvedValueOnce([]);
    const { onRecordSaved, onRecordSaving } = renderStep();
    const user = userEvent.setup();

    await user.click(screen.getByRole("radio", { name: "따로 받음" }));
    await user.type(screen.getByLabelText(/연간 고정상여/), "600");
    await user.tab();
    await user.click(screen.getByRole("radio", { name: "들어 있음" }));

    expect(income.updateBonusTerms).toHaveBeenCalledTimes(1);
    expect(onRecordSaving).not.toHaveBeenCalledWith(false);

    await act(async () => resolveFirst([]));

    await waitFor(() =>
      expect(income.updateBonusTerms).toHaveBeenCalledTimes(2),
    );
    expect(income.updateBonusTerms).toHaveBeenLastCalledWith({
      applyFromIncomeId: "i-1",
      bonusIncluded: true,
      fixedBonusAnnual: null,
    });
    await waitFor(() =>
      expect(onRecordSaving.mock.calls).toEqual([[true], [false]]),
    );
    expect(onRecordSaved).toHaveBeenCalledTimes(2);
  });
});
