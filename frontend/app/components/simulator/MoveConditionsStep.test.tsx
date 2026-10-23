import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { act, createRef } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { EvaluateRequest } from "../../lib/api/scenario";
import * as scenario from "../../lib/api/scenario";
import { MoveConditionsStep } from "./MoveConditionsStep";
import type { StepFormHandle } from "./useDebouncedEmit";

vi.mock("../../lib/api/scenario", () => ({
  getFeasibility: vi.fn(),
}));

type Move = NonNullable<EvaluateRequest["move"]>;

function renderStep() {
  const ref = createRef<StepFormHandle<Move>>();
  const onChange = vi.fn();
  render(<MoveConditionsStep ref={ref} initial={null} onChange={onChange} />);
  return { ref, onChange };
}

function flush(ref: React.RefObject<StepFormHandle<Move> | null>) {
  let result: Move | null = null;
  act(() => {
    result = ref.current!.flush();
  });
  return result as Move | null;
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
    expect(onChange).not.toHaveBeenCalled();
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
});
