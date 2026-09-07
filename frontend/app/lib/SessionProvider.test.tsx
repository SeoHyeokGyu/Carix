import {
  act,
  render,
  renderHook,
  screen,
  waitFor,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "./authApi";
import { ANONYMOUS, type Session } from "./session";
import {
  SessionProvider,
  useRefreshSession,
  useSession,
} from "./SessionProvider";

vi.mock("./authApi", () => ({
  fetchSession: vi.fn(),
}));

function Probe() {
  const session = useSession();
  return (
    <span data-testid="step">
      {session.status === "authenticated"
        ? session.onboardingStep
        : session.status}
    </span>
  );
}

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

  it("초기 세션을 props 로 받아 조회 없이 그대로 노출한다", () => {
    const fetchSpy = vi.spyOn(authApi, "fetchSession");
    const initial: Session = {
      status: "authenticated",
      userId: "u-1",
      email: "a@b.kr",
      onboardingStep: "INCOME",
    };

    render(
      <SessionProvider initialSession={initial}>
        <Probe />
      </SessionProvider>,
    );

    expect(screen.getByTestId("step")).toHaveTextContent("INCOME");
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("refresh 호출 시 fetchSession 응답으로 authenticated 상태로 갱신한다", async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValueOnce({
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "PROFILE",
    });

    render(
      <SessionProvider initialSession={ANONYMOUS}>
        <TestConsumer />
      </SessionProvider>,
    );

    expect(screen.getByTestId("status")).toHaveTextContent("anonymous");

    const refreshButton = screen.getByRole("button", { name: "새로고침" });
    await act(async () => {
      refreshButton.click();
    });

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated");
    });
    expect(screen.getByTestId("email")).toHaveTextContent("user@carix.kr");
  });

  it("refresh 호출 시 fetchSession이 null을 반환하면 anonymous 상태로 갱신한다", async () => {
    const authenticatedInitial: Session = {
      status: "authenticated",
      userId: "u-9",
      email: "old@carix.kr",
      onboardingStep: "COMPLETED",
    };
    vi.mocked(authApi.fetchSession).mockResolvedValueOnce(null);

    render(
      <SessionProvider initialSession={authenticatedInitial}>
        <TestConsumer />
      </SessionProvider>,
    );

    expect(screen.getByTestId("status")).toHaveTextContent("authenticated");

    const refreshButton = screen.getByRole("button", { name: "새로고침" });
    await act(async () => {
      refreshButton.click();
    });

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("anonymous");
    });
  });

  it("refresh 호출 중 에러가 발생하면 anonymous 상태로 처리한다", async () => {
    const authenticatedInitial: Session = {
      status: "authenticated",
      userId: "u-9",
      email: "old@carix.kr",
      onboardingStep: "COMPLETED",
    };
    vi.mocked(authApi.fetchSession).mockRejectedValueOnce(
      new Error("Network Error"),
    );

    render(
      <SessionProvider initialSession={authenticatedInitial}>
        <TestConsumer />
      </SessionProvider>,
    );

    const refreshButton = screen.getByRole("button", { name: "새로고침" });
    await act(async () => {
      refreshButton.click();
    });

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("anonymous");
    });
  });
});
