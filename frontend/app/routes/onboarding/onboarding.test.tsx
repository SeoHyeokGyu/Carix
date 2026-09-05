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

describe("온보딩 라우트 및 레이아웃 (Onboarding)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("개별 화면 (Pages)", () => {
    it("Profile(프로필 입력) 화면을 정상 렌더링한다", () => {
      render(<Profile />);
      expect(screen.getByText("S-05")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "프로필" }),
      ).toBeInTheDocument();
    });

    it("Income(소득 입력) 화면을 정상 렌더링한다", () => {
      render(<Income />);
      expect(screen.getByText("S-06")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "소득 입력" }),
      ).toBeInTheDocument();
    });

    it("Expense(소비 입력) 화면을 정상 렌더링한다", () => {
      render(<Expense />);
      expect(screen.getByText("S-07")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "소비 입력" }),
      ).toBeInTheDocument();
    });

    it("Skills(보유 스킬) 화면을 정상 렌더링한다", () => {
      render(<Skills />);
      expect(screen.getByText("S-08")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "보유 스킬" }),
      ).toBeInTheDocument();
    });

    it("Review(입력 검토) 화면을 정상 렌더링한다", () => {
      render(<Review />);
      expect(screen.getByText("S-09")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "입력 검토" }),
      ).toBeInTheDocument();
    });
  });

  describe("OnboardingLayout (온보딩 공통 레이아웃)", () => {
    it("세션 조회 중일 때는 SessionPending을 렌더링한다", () => {
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

    it("비로그인 상태 시 랜딩 페이지로 리디렉트한다", () => {
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

    it("사용자의 현재 단계와 다른 온보딩 경로 접근 시 현재 단계로 리디렉트한다", () => {
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

    it("현재 진행 단계와 일치하면 AppShell 탭 및 온보딩 화면을 렌더링한다", () => {
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
