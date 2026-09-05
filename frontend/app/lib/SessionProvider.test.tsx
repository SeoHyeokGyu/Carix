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

  it("throws error when useSession is used outside of SessionProvider", () => {
    // Suppress console.error for expected thrown error
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});

    expect(() => {
      renderHook(() => useSession());
    }).toThrow("SessionProvider 안에서만 쓸 수 있습니다");

    spy.mockRestore();
  });

  it("throws error when useRefreshSession is used outside of SessionProvider", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});

    expect(() => {
      renderHook(() => useRefreshSession());
    }).toThrow("SessionProvider 안에서만 쓸 수 있습니다");

    spy.mockRestore();
  });

  it("updates state to authenticated when fetchSession returns session data", async () => {
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

  it("updates state to anonymous when fetchSession returns null", async () => {
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

  it("updates state to anonymous when fetchSession throws an error", async () => {
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

  it("re-fetches and updates session when refresh is called", async () => {
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
