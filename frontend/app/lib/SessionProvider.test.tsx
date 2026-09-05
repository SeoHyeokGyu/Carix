import {
  act,
  render,
  renderHook,
  screen,
  waitFor,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "./authApi";
import {
  SessionProvider,
  useRefreshSession,
  useSession,
} from "./SessionProvider";

vi.mock("./authApi", () => ({
  fetchSession: vi.fn(),
}));

describe("SessionProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  function TestConsumer() {
    const session = useSession();
    const refresh = useRefreshSession();

    return (
      <div>
        <span data-testid="status">{session.status}</span>
        {session.status === "authenticated" && (
          <span data-testid="email">{session.email}</span>
        )}
        <button onClick={() => void refresh()}>새로고침</button>
      </div>
    );
  }

  it("SessionProvider 외부에서 useSession 호출 시 에러를 던진다", () => {
    // Suppress console.error for expected thrown error
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});

    expect(() => {
      renderHook(() => useSession());
    }).toThrow("SessionProvider 안에서만 쓸 수 있습니다");

    spy.mockRestore();
  });

  it("SessionProvider 외부에서 useRefreshSession 호출 시 에러를 던진다", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});

    expect(() => {
      renderHook(() => useRefreshSession());
    }).toThrow("SessionProvider 안에서만 쓸 수 있습니다");

    spy.mockRestore();
  });

  it("fetchSession이 세션 정보를 반환하면 authenticated 상태로 갱신한다", async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValueOnce({
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "PROFILE",
    });

    render(
      <SessionProvider>
        <TestConsumer />
      </SessionProvider>,
    );

    // Initial state is loading
    expect(screen.getByTestId("status")).toHaveTextContent("loading");

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated");
    });

    expect(screen.getByTestId("email")).toHaveTextContent("user@carix.kr");
  });

  it("fetchSession이 null을 반환하면 anonymous 상태로 갱신한다", async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValueOnce(null);

    render(
      <SessionProvider>
        <TestConsumer />
      </SessionProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("anonymous");
    });
  });

  it("fetchSession 호출 중 에러가 발생하면 anonymous 상태로 처리한다", async () => {
    vi.mocked(authApi.fetchSession).mockRejectedValueOnce(
      new Error("Network Error"),
    );

    render(
      <SessionProvider>
        <TestConsumer />
      </SessionProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("anonymous");
    });
  });

  it("refresh 호출 시 세션을 재조회하여 상태를 갱신한다", async () => {
    vi.mocked(authApi.fetchSession)
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({
        userId: "u-2",
        email: "after-login@carix.kr",
        onboardingStep: "COMPLETED",
      });

    render(
      <SessionProvider>
        <TestConsumer />
      </SessionProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("anonymous");
    });

    const refreshButton = screen.getByRole("button", { name: "새로고침" });
    await act(async () => {
      refreshButton.click();
    });

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated");
    });
    expect(screen.getByTestId("email")).toHaveTextContent(
      "after-login@carix.kr",
    );
  });
});
