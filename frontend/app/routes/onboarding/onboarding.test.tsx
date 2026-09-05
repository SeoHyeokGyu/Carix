import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as SessionProviderModule from "../../lib/SessionProvider";
import type { Session } from "../../lib/session";
import Expense from "./expense";
import Income from "./income";
import OnboardingLayout from "./layout";
import Profile from "./profile";
import Review from "./review";
import Skills from "./skills";

vi.mock("../../lib/SessionProvider", () => ({
  useSession: vi.fn(),
}));

describe("Onboarding routes and layout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("pages", () => {
    it("renders Profile page", () => {
      render(<Profile />);
      expect(screen.getByText("S-05")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "프로필" }),
      ).toBeInTheDocument();
    });

    it("renders Income page", () => {
      render(<Income />);
      expect(screen.getByText("S-06")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "소득 입력" }),
      ).toBeInTheDocument();
    });

    it("renders Expense page", () => {
      render(<Expense />);
      expect(screen.getByText("S-07")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "소비 입력" }),
      ).toBeInTheDocument();
    });

    it("renders Skills page", () => {
      render(<Skills />);
      expect(screen.getByText("S-08")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "보유 스킬" }),
      ).toBeInTheDocument();
    });

    it("renders Review page", () => {
      render(<Review />);
      expect(screen.getByText("S-09")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "입력 검토" }),
      ).toBeInTheDocument();
    });
  });

  describe("OnboardingLayout", () => {
    it("renders SessionPending when session is loading", () => {
      vi.mocked(SessionProviderModule.useSession).mockReturnValue({
        status: "loading",
      });

      render(
        <MemoryRouter initialEntries={["/onboarding/profile"]}>
          <OnboardingLayout />
        </MemoryRouter>,
      );

      expect(screen.getByRole("status")).toHaveTextContent("불러오는 중…");
    });

    it("redirects to landing when anonymous", () => {
      vi.mocked(SessionProviderModule.useSession).mockReturnValue({
        status: "anonymous",
      });

      render(
        <MemoryRouter initialEntries={["/onboarding/profile"]}>
          <Routes>
            <Route path="/onboarding/profile" element={<OnboardingLayout />} />
            <Route path="/" element={<div>랜딩 페이지</div>} />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText("랜딩 페이지")).toBeInTheDocument();
    });

    it("redirects to user current step if navigating to wrong step", () => {
      vi.mocked(SessionProviderModule.useSession).mockReturnValue({
        status: "authenticated",
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE",
      });

      render(
        <MemoryRouter initialEntries={["/onboarding/skills"]}>
          <Routes>
            <Route path="/onboarding/*" element={<OnboardingLayout />} />
            <Route
              path="/onboarding/profile"
              element={<div>프로필로 리디렉트됨</div>}
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText("프로필로 리디렉트됨")).toBeInTheDocument();
    });

    it("renders AppShell with onboarding steps when step matches user onboardingStep", () => {
      const session: Session = {
        status: "authenticated",
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE",
      };
      vi.mocked(SessionProviderModule.useSession).mockReturnValue(session);

      render(
        <MemoryRouter initialEntries={["/onboarding/profile"]}>
          <Routes>
            <Route element={<OnboardingLayout />}>
              <Route
                path="/onboarding/profile"
                element={<div>프로필 온보딩 본문</div>}
              />
            </Route>
          </Routes>
        </MemoryRouter>,
      );

      expect(
        screen.getByRole("navigation", { name: "온보딩 단계" }),
      ).toBeInTheDocument();
      expect(screen.getByText("프로필 온보딩 본문")).toBeInTheDocument();
    });
  });
});
