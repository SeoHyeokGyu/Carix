import { render, screen } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as authApi from "../lib/authApi";
import type { Session } from "../lib/session";
import * as sessionLib from "../lib/session";
import ProtectedLayout, { clientLoader, HydrateFallback } from "./protected";

const { ANONYMOUS } = sessionLib;

vi.mock("../lib/authApi", () => ({
  fetchSession: vi.fn(),
}));

describe("보호 레이아웃 (protected)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionLib.resetSession();
  });

  // SessionProvider 는 최종 리뷰에서 제거됐다 — 세션은 module-level 캐시(session.ts)가
  // 정본이고 하위 layout 이 각자 requireSession() 으로 읽는다. 이 레이아웃은 하위
  // 라우트를 그대로 렌더하기만 하면 된다.
  it("하위 라우트를 그대로 렌더한다", async () => {
    const session: Session = {
      status: "authenticated",
      userId: "u-1",
      email: "a@b.kr",
      onboardingStep: "PROFILE",
    };

    const Stub = createRoutesStub([
      {
        path: "/",
        Component: ProtectedLayout,
        HydrateFallback,
        loader: () => ({ session }),
        children: [
          {
            index: true,
            Component: () => <div data-testid="outlet">본문</div>,
          },
        ],
      },
    ]);

    render(<Stub initialEntries={["/"]} />);

    expect(await screen.findByTestId("outlet")).toBeInTheDocument();
  });

  describe("clientLoader", () => {
    it("requireSession 이 돌려준 세션을 { session } 형태로 반환한다", async () => {
      vi.mocked(authApi.fetchSession).mockResolvedValueOnce({
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE",
      });

      const result = await clientLoader();

      expect(result).toEqual({
        session: {
          status: "authenticated",
          userId: "u-1",
          email: "user@carix.kr",
          onboardingStep: "PROFILE",
        },
      });
    });

    it("세션 조회에 실패해도 anonymous 세션을 { session } 형태로 반환한다", async () => {
      vi.mocked(authApi.fetchSession).mockRejectedValueOnce(
        new Error("Network Error"),
      );

      const result = await clientLoader();

      expect(result).toEqual({ session: ANONYMOUS });
    });
  });
});
