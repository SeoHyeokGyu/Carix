import { render, screen } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as authApi from "../../lib/authApi";
import { getSession, resetSession, setSession } from "../../lib/session";
import { clientLoader as mypageLayoutLoader } from "./layout";
import { clientAction as mypageProfileAction } from "./profile";

describe("마이페이지 라우트 및 레이아웃 (Mypage)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 세션 캐시는 모듈 전역이라 앞 테스트 값을 물려받을 수 있다 — 매번 지우고 각 테스트가
    // setSession 으로 필요한 값을 명시적으로 채운다.
    resetSession();
  });

  describe("개별 화면 (Pages)", () => {
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
  });
});
