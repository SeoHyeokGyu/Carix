import { render, screen } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "./lib/authApi";
import * as sessionLib from "./lib/session";
import type { Session } from "./lib/session";
import Root, { HydrateFallback, Layout, clientLoader } from "./root";

const { ANONYMOUS } = sessionLib;

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return {
    ...actual,
    Meta: () => <meta data-testid="meta" />,
    Links: () => <link data-testid="links" />,
    Scripts: () => <script data-testid="scripts" />,
    ScrollRestoration: () => <div data-testid="scroll-restoration" />,
  };
});

vi.mock("./lib/SessionProvider", () => ({
  SessionProvider: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="session-provider">{children}</div>
  ),
}));

vi.mock("./lib/authApi", () => ({
  fetchSession: vi.fn(),
}));

describe("루트 컴포넌트 (root)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("Layout이 메타데이터, 폰트 링크, 스크립트 및 자식 요소를 렌더링한다", () => {
    render(
      <Layout>
        <div data-testid="child">테스트 자식 엘리먼트</div>
      </Layout>,
    );

    expect(screen.getByTestId("child")).toBeInTheDocument();
    expect(screen.getByTestId("scroll-restoration")).toBeInTheDocument();
    expect(document.querySelector('[data-testid="meta"]')).not.toBeNull();
    expect(document.querySelector('[data-testid="links"]')).not.toBeNull();
  });

  it("Root가 loader 로 받은 세션을 SessionProvider 에 전달하고 Outlet 을 렌더링한다", async () => {
    const Stub = createRoutesStub([
      {
        path: "/",
        Component: Root,
        HydrateFallback,
        loader: () => ({ session: ANONYMOUS }),
        children: [
          {
            index: true,
            Component: () => <div data-testid="outlet">아울렛 내용</div>,
          },
        ],
      },
    ]);

    render(<Stub initialEntries={["/"]} />);

    expect(await screen.findByTestId("session-provider")).toBeInTheDocument();
    expect(screen.getByTestId("outlet")).toBeInTheDocument();
  });

  describe("clientLoader", () => {
    it("세션 조회에 성공하면 authenticated 세션을 반환하고 캐시에 저장한다", async () => {
      const setSessionSpy = vi.spyOn(sessionLib, "setSession");
      vi.mocked(authApi.fetchSession).mockResolvedValueOnce({
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE",
      });

      const result = await clientLoader();

      const expected: Session = {
        status: "authenticated",
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE",
      };
      expect(result).toEqual({ session: expected });
      expect(setSessionSpy).toHaveBeenCalledWith(expected);
    });

    it("세션 조회에 실패하면 anonymous 세션을 반환하고 캐시에 저장한다", async () => {
      const setSessionSpy = vi.spyOn(sessionLib, "setSession");
      vi.mocked(authApi.fetchSession).mockRejectedValueOnce(
        new Error("Network Error"),
      );

      const result = await clientLoader();

      expect(result).toEqual({ session: ANONYMOUS });
      expect(setSessionSpy).toHaveBeenCalledWith(ANONYMOUS);
    });
  });
});
