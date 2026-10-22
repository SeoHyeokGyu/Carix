import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { EvaluateRequest } from "../../lib/api/scenario";
import * as scenario from "../../lib/api/scenario";
import { MoveConditionsStep } from "./MoveConditionsStep";

vi.mock("../../lib/api/scenario", () => ({
  putDraft: vi.fn(),
  getFeasibility: vi.fn(),
}));

const draft: EvaluateRequest = { stay: { raisePercent: 3 } };

describe("MoveConditionsStep", () => {
  beforeEach(() => vi.clearAllMocks());

  it("실업급여를 고르지 않고 제출하면 오류가 보이고 저장하지 않는다", async () => {
    const onDone = vi.fn();
    render(
      <MoveConditionsStep draft={draft} onBack={vi.fn()} onDone={onDone} />,
    );
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/이직 연봉/), "7000");
    await user.click(screen.getByRole("button", { name: "비교 결과 보기" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "실업급여 여부를 선택해 주세요.",
    );
    expect(scenario.putDraft).not.toHaveBeenCalled();
    expect(onDone).not.toHaveBeenCalled();
  });
});
