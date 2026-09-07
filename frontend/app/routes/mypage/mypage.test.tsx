import { render, screen } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setSession } from "../../lib/session";
import MypageLayout, { clientLoader as mypageLayoutLoader } from "./layout";
import MypageProfile from "./profile";
import MypageRecords from "./records";

describe("마이페이지 라우트 및 레이아웃 (Mypage)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("개별 화면 (Pages)", () => {
    it("MypageProfile(설정) 화면을 정상 렌더링한다", () => {
      render(<MypageProfile />);
      expect(screen.getByText("S-13")).toBeInTheDocument();
      expect(screen.getByRole("heading", { name: "설정" })).toBeInTheDocument();
    });

    it("MypageRecords(이력 관리) 화면을 정상 렌더링한다", () => {
      render(<MypageRecords />);
      expect(screen.getByText("S-12")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "이력 관리" }),
      ).toBeInTheDocument();
    });
  });

  describe("MypageLayout (마이페이지 공통 레이아웃)", () => {
    it("비로그인(anonymous) 상태 시 랜딩 페이지로 리디렉트한다", async () => {
      setSession({ status: "anonymous" });

      const Stub = createRoutesStub([
        {
          path: "/mypage/profile",
          loader: mypageLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>마이페이지</div>,
        },
        { path: "/", Component: () => <div>랜딩 페이지</div> },
      ]);

      render(<Stub initialEntries={["/mypage/profile"]} />);

      expect(await screen.findByText("랜딩 페이지")).toBeInTheDocument();
    });

    it("로그인 상태면 온보딩 완료 여부와 관계없이 AppShell 탭을 렌더링한다", async () => {
      setSession({
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE", // Even incomplete onboarding is allowed in mypage
      });

      const Stub = createRoutesStub([
        {
          path: "/mypage/profile",
          loader: mypageLayoutLoader,
          HydrateFallback: () => null,
          Component: MypageLayout,
          children: [
            { index: true, Component: () => <div>설정 페이지 내용</div> },
          ],
        },
      ]);

      render(<Stub initialEntries={["/mypage/profile"]} />);

      expect(
        await screen.findByRole("navigation", { name: "마이페이지" }),
      ).toBeInTheDocument();
      expect(screen.getByText("설정 페이지 내용")).toBeInTheDocument();
    });
  });
});
