import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as authApi from "../../lib/authApi";
import { BetaNotice } from "./BetaNotice";

vi.mock("../../lib/authApi", async () => ({
  ...(await vi.importActual<typeof authApi>("../../lib/authApi")),
  startGuest: vi.fn(),
}));

function renderNotice() {
  render(
    <MemoryRouter>
      <BetaNotice />
    </MemoryRouter>,
  );
}

describe("BetaNotice 게스트 시작 실패", () => {
  beforeEach(() => vi.clearAllMocks());

  it("네트워크 예외 원문은 숨기고 사용자 문구를 보여주며 버튼을 다시 연다", async () => {
    vi.mocked(authApi.startGuest).mockRejectedValueOnce(
      new Error("Failed to fetch"),
    );

    renderNotice();
    fireEvent.click(screen.getByRole("button", { name: "가입 없이 시작하기" }));

    expect(await screen.findByText(/요청을 처리하지 못했습니다/)).toBeVisible();
    expect(screen.queryByText(/Failed to fetch/)).not.toBeInTheDocument();
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "가입 없이 시작하기" }),
      ).toBeEnabled(),
    );
  });

  it("속도 제한 429 는 서버 문구를 보여준다", async () => {
    vi.mocked(authApi.startGuest).mockRejectedValueOnce({
      code: "RATE_LIMITED",
      status: 429,
      message: "요청이 많아 잠시 후 다시 시도해 주세요",
    });

    renderNotice();
    fireEvent.click(screen.getByRole("button", { name: "가입 없이 시작하기" }));

    expect(
      await screen.findByText("요청이 많아 잠시 후 다시 시도해 주세요"),
    ).toBeVisible();
  });
});
