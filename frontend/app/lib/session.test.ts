import { beforeEach, describe, expect, it, vi } from "vitest";

import * as authApi from "./authApi";
import { ANONYMOUS, requireSession, resetSession, setSession } from "./session";

vi.mock("./authApi", () => ({
  fetchSession: vi.fn(),
}));

describe("requireSession / setSession / resetSession", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetSession();
  });

  it("캐시가 비어 있으면 fetchSession 을 불러 authenticated 세션을 채운다", async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValueOnce({
      userId: "u-1",
      email: "a@b.kr",
      onboardingStep: "PROFILE",
      guest: false,
    });

    const session = await requireSession();

    expect(session).toEqual({
      status: "authenticated",
      userId: "u-1",
      email: "a@b.kr",
      onboardingStep: "PROFILE",
      guest: false,
    });
    // 이후 호출은 캐시를 쓴다.
    await requireSession();
    expect(authApi.fetchSession).toHaveBeenCalledTimes(1);
  });

  it("fetchSession 이 실패해도 anonymous 로 처리하고 예외를 던지지 않는다", async () => {
    vi.mocked(authApi.fetchSession).mockRejectedValueOnce(
      new Error("Network Error"),
    );

    await expect(requireSession()).resolves.toEqual(ANONYMOUS);
  });

  it("캐시가 비어 있는 동안 동시에 여러 번 불러도 fetchSession 은 한 번만 나간다", async () => {
    let resolveFetch!: (
      value: Awaited<ReturnType<typeof authApi.fetchSession>>,
    ) => void;
    vi.mocked(authApi.fetchSession).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveFetch = resolve;
      }),
    );

    const call1 = requireSession();
    const call2 = requireSession();

    resolveFetch({
      userId: "u-1",
      email: "a@b.kr",
      onboardingStep: "SKILLS",
      guest: false,
    });

    const [session1, session2] = await Promise.all([call1, call2]);

    expect(authApi.fetchSession).toHaveBeenCalledTimes(1);
    expect(session1).toEqual(session2);
    expect(session1).toEqual({
      status: "authenticated",
      userId: "u-1",
      email: "a@b.kr",
      onboardingStep: "SKILLS",
      guest: false,
    });
  });

  it("resetSession 을 부르면 캐시가 지워져 다음 호출이 다시 조회한다", async () => {
    setSession({ status: "anonymous" });
    resetSession();
    vi.mocked(authApi.fetchSession).mockResolvedValueOnce({
      userId: "u-2",
      email: "b@b.kr",
      onboardingStep: "EXPENSE",
      guest: false,
    });

    const session = await requireSession();

    expect(authApi.fetchSession).toHaveBeenCalledTimes(1);
    expect(session).toEqual({
      status: "authenticated",
      userId: "u-2",
      email: "b@b.kr",
      onboardingStep: "EXPENSE",
      guest: false,
    });
  });
});
