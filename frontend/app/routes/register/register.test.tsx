import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as authApi from "../../lib/authApi";
import * as sessionLib from "../../lib/session";
import Register from "./register";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock("../../lib/authApi", () => ({
  register: vi.fn(),
}));

describe("회원가입 라우트 (Register)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("제목 및 로그인 링크가 포함된 회원가입 폼을 렌더링한다", () => {
    render(
      <MemoryRouter>
        <Register />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("heading", { name: "회원가입" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("S-03")).not.toBeInTheDocument();
    const link = screen.getByRole("link", { name: "로그인" });
    expect(link).toHaveAttribute("href", "/login");
  });

  it("가입 성공 시 세션 캐시를 채우고 온보딩 단계 경로로 이동시킨다", async () => {
    const setSessionSpy = vi.spyOn(sessionLib, "setSession");
    vi.mocked(authApi.register).mockResolvedValueOnce({
      userId: "u-10",
      email: "newuser@carix.kr",
      onboardingStep: "PROFILE",
    });

    render(
      <MemoryRouter>
        <Register />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText("이메일"), {
      target: { value: "newuser@carix.kr" },
    });
    fireEvent.change(screen.getByLabelText(/비밀번호/), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: "계정 만들기" }));

    await waitFor(() => {
      expect(authApi.register).toHaveBeenCalledWith(
        "newuser@carix.kr",
        "password123",
      );
      expect(setSessionSpy).toHaveBeenCalledWith({
        status: "authenticated",
        userId: "u-10",
        email: "newuser@carix.kr",
        onboardingStep: "PROFILE",
      });
      expect(mockNavigate).toHaveBeenCalledWith("/onboarding/profile", {
        replace: true,
      });
    });
  });
});
