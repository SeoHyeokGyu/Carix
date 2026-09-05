import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "../../lib/authApi";
import Login from "./login";

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
  login: vi.fn(),
}));

describe("Login route", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders login form with title and link to register", () => {
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "로그인" })).toBeInTheDocument();
    expect(screen.getByText("S-02")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "가입하기" });
    expect(link).toHaveAttribute("href", "/register");
  });

  it("handles successful login, refreshes session, and navigates to onboardingStep", async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "COMPLETED",
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
      expect(mockRefresh).toHaveBeenCalled();
      expect(mockNavigate).toHaveBeenCalledWith("/dashboard", {
        replace: true,
      });
    });
  });

  it("navigates to specific onboarding step when onboarding is not completed", async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "PROFILE",
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
