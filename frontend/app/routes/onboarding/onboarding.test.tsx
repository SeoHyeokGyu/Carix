import { render, screen } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setSession } from "../../lib/session";
import Expense from "./expense";
import Income from "./income";
import OnboardingLayout, { clientLoader as onboardingLayoutLoader } from "./layout";
import Profile from "./profile";
import Review from "./review";
import Skills from "./skills";

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
    it("비로그인 상태면 랜딩 페이지로 보낸다", async () => {
      setSession({ status: "anonymous" });

      const Stub = createRoutesStub([
        {
          path: "/onboarding/profile",
          loader: onboardingLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>온보딩</div>,
        },
        { path: "/", Component: () => <div>랜딩 페이지</div> },
      ]);

      render(<Stub initialEntries={["/onboarding/profile"]} />);

      expect(await screen.findByText("랜딩 페이지")).toBeInTheDocument();
    });

    it("사용자의 현재 단계와 다른 온보딩 경로 접근 시 현재 단계로 리디렉트한다", async () => {
      setSession({
        status: "authenticated",
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE",
      });

      const Stub = createRoutesStub([
        {
          path: "/onboarding/skills",
          loader: onboardingLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>스킬</div>,
        },
        {
          path: "/onboarding/profile",
          Component: () => <div>프로필로 리디렉트됨</div>,
        },
      ]);

      render(<Stub initialEntries={["/onboarding/skills"]} />);

      expect(
        await screen.findByText("프로필로 리디렉트됨"),
      ).toBeInTheDocument();
    });

    it("온보딩 완료 사용자가 소득 화면을 열면 대시보드로 보낸다", async () => {
      setSession({
        status: "authenticated",
        userId: "u-1",
        email: "a@b.kr",
        onboardingStep: "COMPLETED",
      });

      const Stub = createRoutesStub([
        {
          path: "/onboarding/income",
          loader: onboardingLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>온보딩</div>,
        },
        { path: "/dashboard", Component: () => <div>대시보드</div> },
      ]);

      render(<Stub initialEntries={["/onboarding/income"]} />);

      expect(await screen.findByText("대시보드")).toBeInTheDocument();
    });

    it("현재 진행 단계와 일치하면 AppShell 탭 및 온보딩 화면을 렌더링한다", async () => {
      setSession({
        status: "authenticated",
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE",
      });

      const Stub = createRoutesStub([
        {
          path: "/onboarding/profile",
          loader: onboardingLayoutLoader,
          HydrateFallback: () => null,
          Component: OnboardingLayout,
          children: [
            { index: true, Component: () => <div>프로필 온보딩 본문</div> },
          ],
        },
      ]);

      render(<Stub initialEntries={["/onboarding/profile"]} />);

      expect(
        await screen.findByRole("navigation", { name: "온보딩 단계" }),
      ).toBeInTheDocument();
      expect(screen.getByText("프로필 온보딩 본문")).toBeInTheDocument();
    });
  });
});
