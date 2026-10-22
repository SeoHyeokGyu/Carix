import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { InlineAlert } from "./inline-alert";

describe("InlineAlert", () => {
  it("onRetry 가 있으면 「다시 시도」 를 두고, 다시 불러오는 중이면 막는다", async () => {
    const onRetry = vi.fn();
    const { rerender } = render(
      <InlineAlert message="실패" onRetry={onRetry} />,
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "다시 시도" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "다시 시도" })).toHaveAttribute(
      "data-variant",
      "outline",
    );
    rerender(<InlineAlert message="실패" onRetry={onRetry} retrying />);
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeDisabled();
  });
});
