import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { act, createRef } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import * as scenario from "../../lib/api/scenario";
import { MoveConditionsStep, type OfferEmit } from "./MoveConditionsStep";
import type { StepFormHandle } from "./useDebouncedEmit";

vi.mock("../../lib/api/scenario", () => ({
  getFeasibility: vi.fn(),
}));

function renderStep() {
  const ref = createRef<StepFormHandle<OfferEmit>>();
  const onChange = vi.fn();
  const onInvalid = vi.fn();
  render(
    <MoveConditionsStep
      ref={ref}
      initial={null}
      onChange={onChange}
      onInvalid={onInvalid}
    />,
  );
  return { ref, onChange, onInvalid };
}

function flush(ref: React.RefObject<StepFormHandle<OfferEmit> | null>) {
  let result: OfferEmit | null = null;
  act(() => {
    result = ref.current!.flush();
  });
  return result as OfferEmit | null;
}

describe("MoveConditionsStep", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(scenario.getFeasibility).mockResolvedValue({
      status: "OK",
      sampleSize: 30,
      minRequired: 10,
      feasibilityScore: 0.5,
      targetPercentile: 60,
      difficultyGrade: "REALISTIC",
      feedbackMessage: null,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("실업급여를 고르지 않으면 flush 가 null 이고 반영하지 않는다", async () => {
    const { ref, onChange } = renderStep();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/새 회사 연봉/), "7000");

    expect(flush(ref)).toBeNull();
    // 처음 그릴 때 빈 폼은 "blank" 로 한 번 나간다. 오퍼 값은 나가면 안 된다.
    expect(onChange).not.toHaveBeenCalledWith(
      expect.objectContaining({ grossSalary: expect.anything() }),
      expect.anything(),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "실업급여 여부를 선택해 주세요.",
    );
  });

  it("값을 바꾸고 400ms 안에 flush 하면 방금 값이 반영된다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { ref, onChange } = renderStep();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    await user.click(screen.getByRole("radio", { name: "받음" }));
    await user.type(screen.getByLabelText(/새 회사 연봉/), "7000");
    act(() => {
      vi.advanceTimersByTime(100);
    });

    expect(flush(ref)).toEqual(
      expect.objectContaining({ grossSalary: 70_000_000 }),
    );
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ grossSalary: 70_000_000 }),
      "edit",
    );
    const calls = onChange.mock.calls.length;

    act(() => {
      vi.advanceTimersByTime(400);
    });

    expect(onChange).toHaveBeenCalledTimes(calls);
  });

  it("모든 칸을 비우면 오류 없이 비어 있음을 낸다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { onChange } = renderStep();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const gross = screen.getByLabelText(/새 회사 연봉/);

    await user.type(gross, "7200");
    await user.clear(gross);
    act(() => {
      vi.advanceTimersByTime(400);
    });

    expect(onChange).toHaveBeenLastCalledWith("blank", "edit");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("연봉만 넣고 실업급여를 안 고르면 benefit 으로 무효", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { onInvalid } = renderStep();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    await user.type(screen.getByLabelText(/새 회사 연봉/), "7200");
    act(() => {
      vi.advanceTimersByTime(400);
    });

    expect(onInvalid).toHaveBeenLastCalledWith("benefit");
  });

  it("일회성 비용만 넣고 연봉이 비면 gross 로 무효", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { onInvalid } = renderStep();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    await user.type(screen.getByLabelText(/일회성 비용/), "300");
    act(() => {
      vi.advanceTimersByTime(400);
    });

    expect(onInvalid).toHaveBeenLastCalledWith("gross");
  });
});
