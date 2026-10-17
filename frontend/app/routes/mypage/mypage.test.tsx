import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as authApi from "../../lib/authApi";
import * as onboardingApi from "../../lib/onboardingApi";
import { getSession, resetSession, setSession } from "../../lib/session";
import { confirmDialog } from "../../test-utils/confirmDialog";
import MypageLayout, { clientLoader as mypageLayoutLoader } from "./layout";
import MypageProfile, { clientAction as mypageProfileAction } from "./profile";

describe("마이페이지 라우트 및 레이아웃 (Mypage)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 세션 캐시는 모듈 전역이라 앞 테스트 값을 물려받을 수 있다 — 매번 지우고 각 테스트가
    // setSession 으로 필요한 값을 명시적으로 채운다.
    resetSession();
  });

  describe("개별 화면 (Pages)", () => {
    it("MypageProfile(설정) 화면을 정상 렌더링한다", async () => {
      const Stub = createRoutesStub([
        {
          path: "/mypage/profile",
          Component: MypageProfile,
        },
      ]);
      render(<Stub initialEntries={["/mypage/profile"]} />);
      expect(
        await screen.findByRole("heading", { name: "설정" }),
      ).toBeInTheDocument();
      expect(screen.queryByText("S-13")).not.toBeInTheDocument();
      expect(screen.getByText("온보딩 재설정")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "온보딩 처음부터 다시 시작" }),
      ).toBeInTheDocument();
    });

    it("온보딩 다시 시작을 누르면 resetOnboarding 을 부르고 프로필로 이동한다", async () => {
      setSession({
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "COMPLETED",
      });

      const resetSpy = vi
        .spyOn(onboardingApi, "resetOnboarding")
        .mockResolvedValue({
          currentStep: "PROFILE",
          completed: false,
        });

      const Stub = createRoutesStub([
        {
          path: "/mypage/profile",
          Component: MypageProfile,
          action: mypageProfileAction,
        },
        {
          path: "/onboarding/profile",
          Component: () => <div>프로필 화면</div>,
        },
      ]);

      render(<Stub initialEntries={["/mypage/profile"]} />);
      const user = userEvent.setup();

      await user.click(
        await screen.findByRole("button", {
          name: "온보딩 처음부터 다시 시작",
        }),
      );
      await confirmDialog(user, "다시 시작");

      await waitFor(() => expect(resetSpy).toHaveBeenCalledTimes(1));
      expect(await screen.findByText("프로필 화면")).toBeInTheDocument();
    });

    it("logout 의도는 현재 세션만 폐기하고 로그인 화면으로 보낸다", async () => {
      setSession({
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "COMPLETED",
      });
      const logoutSpy = vi.spyOn(authApi, "logout").mockResolvedValue();
      const logoutAllSpy = vi.spyOn(authApi, "logoutAll");
      const form = new FormData();
      form.set("intent", "logout");

      const result = await mypageProfileAction({
        request: new Request("http://localhost/mypage/profile", {
          method: "POST",
          body: form,
        }),
      });

      expect(logoutSpy).toHaveBeenCalledTimes(1);
      expect(logoutAllSpy).not.toHaveBeenCalled();
      expect(getSession()).toEqual({ status: "anonymous" });
      expect(result).toBeInstanceOf(Response);
      expect((result as Response).headers.get("Location")).toBe("/login");
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
        await screen.findByRole("navigation", { name: "주 메뉴" }),
      ).toBeInTheDocument();
      expect(screen.getByText("설정 페이지 내용")).toBeInTheDocument();

      // 상단 메인 메뉴바의 항목들이 설정 화면에서도 변경되지 않고 그대로 유지되는지 검증
      for (const name of ["대시보드", "추이 분석", "시뮬레이터", "입력 관리"]) {
        expect(screen.getByRole("link", { name })).toBeInTheDocument();
      }
      // 지난 판정(S-11)은 주 메뉴가 아니라 판정 화면 안의 링크로 들어간다.
      expect(
        screen.queryByRole("link", { name: "지수 기록" }),
      ).not.toBeInTheDocument();
      expect(screen.getByRole("link", { name: "도움말" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "내 정보" })).toHaveAttribute(
        "data-active",
        "true",
      );
    });
  });
});
