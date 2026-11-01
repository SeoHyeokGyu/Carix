import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as authApi from "../../lib/authApi";
import * as sessionLib from "../../lib/session";
import Login from "./login";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock("../../lib/authApi", () => ({
  login: vi.fn(),
}));

describe("로그인 라우트 (Login)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("로그인 성공 시 세션 캐시를 채우고 온보딩 완료 사용자를 대시보드로 이동시킨다", async () => {
    const setSessionSpy = vi.spyOn(sessionLib, "setSession");
    vi.mocked(authApi.login).mockResolvedValueOnce({
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "COMPLETED",
      guest: false,
    });

    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText("이메일"), {
      target: { value: "user@carix.kr" },
    });
    fireEvent.change(screen.getByLabelText(/비밀번호/), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith(
        "user@carix.kr",
        "password123",
      );
      expect(setSessionSpy).toHaveBeenCalledWith({
        status: "authenticated",
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "COMPLETED",
        guest: false,
      });
      expect(mockNavigate).toHaveBeenCalledWith("/dashboard", {
        replace: true,
      });
    });
  });

  it("온보딩 미완료 사용자는 해당 온보딩 단계 경로로 이동시킨다", async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "PROFILE",
      guest: false,
    });

    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText("이메일"), {
      target: { value: "user@carix.kr" },
    });
    fireEvent.change(screen.getByLabelText(/비밀번호/), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/onboarding/profile", {
        replace: true,
      });
    });
  });
});
