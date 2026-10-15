import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { InlineAlert } from "./inline-alert";

describe("InlineAlert", () => {
  it("위험 색 한 줄 알림이고 버튼은 받은 것만 둔다", () => {
    render(<InlineAlert message="요청을 처리하지 못했습니다." />);
    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("요청을 처리하지 못했습니다.");
    expect(screen.getByText("요청을 처리하지 못했습니다.")).toHaveClass(
      "text-carix-danger",
    );
    expect(screen.queryByRole("button")).toBeNull();
  });

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

  it("onDismiss 가 있으면 닫기 버튼을 둔다", async () => {
    const onDismiss = vi.fn();
    render(<InlineAlert message="실패" onDismiss={onDismiss} />);
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "알림 닫기" }));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });
});
