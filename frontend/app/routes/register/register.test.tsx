import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "../../lib/authApi";
import Register from "./register";

const mockNavigate = vi.fn();
const mockRefresh = vi.fn().mockResolvedValue(undefined);

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock("../../lib/SessionProvider", () => ({
  useRefreshSession: () => mockRefresh,
}));

vi.mock("../../lib/authApi", () => ({
  register: vi.fn(),
}));

describe("Register route", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders register form with title and link to login", () => {
    render(
      <MemoryRouter>
        <Register />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("heading", { name: "회원가입" }),
    ).toBeInTheDocument();
    expect(screen.getByText("S-03")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "로그인" });
    expect(link).toHaveAttribute("href", "/login");
  });

  it("handles successful registration, refreshes session, and navigates to onboardingStep", async () => {
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
      expect(mockRefresh).toHaveBeenCalled();
      expect(mockNavigate).toHaveBeenCalledWith("/onboarding/profile", {
        replace: true,
      });
    });
  });
});
