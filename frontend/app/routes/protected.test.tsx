import { render, screen } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "../lib/authApi";
import * as sessionLib from "../lib/session";
import type { Session } from "../lib/session";
import * as SessionProviderModule from "../lib/SessionProvider";
import ProtectedLayout, { HydrateFallback, clientLoader } from "./protected";

const { ANONYMOUS } = sessionLib;

vi.mock("../lib/authApi", () => ({
  fetchSession: vi.fn(),
}));

vi.mock("../lib/SessionProvider", () => ({
  SessionProvider: vi.fn(({ children }: { children: React.ReactNode }) => (
    <div data-testid="session-provider">{children}</div>
  )),
}));

describe("보호 레이아웃 (protected)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionLib.resetSession();
  });

  it("loader 가 반환한 세션을 SessionProvider 에 그대로 전달한다", async () => {
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
          { index: true, Component: () => <div data-testid="outlet">본문</div> },
        ],
      },
    ]);

    render(<Stub initialEntries={["/"]} />);

    expect(await screen.findByTestId("session-provider")).toBeInTheDocument();
    expect(screen.getByTestId("outlet")).toBeInTheDocument();

    const providerProps = vi.mocked(SessionProviderModule.SessionProvider).mock
      .calls[0]?.[0];
    expect(providerProps).toMatchObject({ initialSession: session });
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
