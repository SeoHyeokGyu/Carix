import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type {
  EvaluateRequest,
  EvaluationResponse,
} from "../../lib/api/scenario";
import { evaluate } from "../../lib/api/scenario";
import { useEvaluation } from "./useEvaluation";

vi.mock("../../lib/api/scenario", () => ({ evaluate: vi.fn() }));

const R1 = { tag: "R1" } as unknown as EvaluateRequest;
const R2 = { tag: "R2" } as unknown as EvaluateRequest;
const RESULT = { stay: {} } as unknown as EvaluationResponse;
const evaluateMock = vi.mocked(evaluate);

interface Props {
  request: EvaluateRequest | null;
  revision: number;
  paused: boolean;
}

const render = (initial: Props) =>
  renderHook(
    ({ request, revision, paused }: Props) =>
      useEvaluation(true, request, () => {}, { revision, paused }),
    { initialProps: initial },
  );

describe("useEvaluation", () => {
  beforeEach(() => {
    evaluateMock.mockReset();
    evaluateMock.mockResolvedValue(RESULT);
  });

  it("일시 정지 중 바뀐 입력은 풀린 뒤 최신 값으로 한 번만 평가한다", async () => {
    const view = render({ request: R1, revision: 0, paused: true });
    view.rerender({ request: R2, revision: 0, paused: true });
    expect(evaluateMock).not.toHaveBeenCalled();
    expect(view.result.current.pending).toBe(true);

    view.rerender({ request: R2, revision: 0, paused: false });
    await waitFor(() => expect(view.result.current.pending).toBe(false));
    expect(evaluateMock).toHaveBeenCalledTimes(1);
    expect(evaluateMock).toHaveBeenCalledWith(R2);
  });

  it("일시 정지 중에는 받아 둔 결과를 유지하고 pending 으로 알린다", async () => {
    const view = render({ request: R1, revision: 0, paused: false });
    await waitFor(() => expect(view.result.current.pending).toBe(false));

    view.rerender({ request: R2, revision: 0, paused: true });
    expect(view.result.current.evaluation.result).toBe(RESULT);
    expect(view.result.current.pending).toBe(true);
    expect(evaluateMock).toHaveBeenCalledTimes(1);
  });

  it("revision 이 오르면 같은 요청을 다시 평가한다", async () => {
    const view = render({ request: R1, revision: 0, paused: false });
    await waitFor(() => expect(view.result.current.pending).toBe(false));
    expect(evaluateMock).toHaveBeenCalledTimes(1);

    act(() => view.rerender({ request: R1, revision: 1, paused: false }));
    await waitFor(() => expect(evaluateMock).toHaveBeenCalledTimes(2));
    expect(evaluateMock).toHaveBeenLastCalledWith(R1);
  });
});
