import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";

import * as authApi from "../../lib/authApi";
import { getSession, resetSession, setSession } from "../../lib/session";
import { AccountMenu } from "./AccountMenu";

vi.mock("../../lib/authApi", () => ({ logout: vi.fn() }));

// Radix 팝퍼가 트리거 크기를 재려고 ResizeObserver 를 부르는데 jsdom 에는 없다.
beforeAll(() => {
  globalThis.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
});

function renderAt(
  path: string,
  action: (args: { request: Request }) => unknown = () => null,
) {
  const Stub = createRoutesStub([
    { path: "/dashboard", Component: AccountMenu },
    { path: "/mypage/profile", Component: AccountMenu, action },
    { path: "/mypage/records", Component: AccountMenu },
    { path: "/", Component: () => <div>랜딩 화면</div> },
  ]);
  render(<Stub initialEntries={[path]} />);
  return action;
}

async function openMenu() {
  const user = userEvent.setup();
  const trigger = await screen.findByRole("button", { name: "내 정보" });
  trigger.focus();
  await user.keyboard("{Enter}");
  return user;
}

describe("AccountMenu", () => {
  it("로그아웃을 고르면 설정 화면 action 에 logout 의도를 보낸다", async () => {
    const action = vi.fn(async ({ request }: { request: Request }) => {
      const form = await request.formData();
      return { intent: form.get("intent") };
    });
    renderAt("/dashboard", action);
    const user = await openMenu();

    await user.click(await screen.findByRole("menuitem", { name: "로그아웃" }));

    await vi.waitFor(() => expect(action).toHaveBeenCalledTimes(1));
    expect(await action.mock.results[0].value).toEqual({ intent: "logout" });
  });

  describe("게스트 체험 끝내기", () => {
    afterEach(() => resetSession());

    async function confirmEndTrial() {
      setSession({
        status: "authenticated",
        userId: "u1",
        email: null,
        onboardingStep: "PROFILE",
        guest: true,
      });
      renderAt("/dashboard");
      const user = await openMenu();
      await user.click(
        await screen.findByRole("menuitem", { name: "체험 끝내기" }),
      );
      await user.click(
        await screen.findByRole("button", { name: "체험 끝내기" }),
      );
    }

    it("로그아웃에_성공하면_세션을_비우고_랜딩으로_간다", async () => {
      vi.mocked(authApi.logout).mockResolvedValue();

      await confirmEndTrial();

      expect(await screen.findByText("랜딩 화면")).toBeInTheDocument();
      expect(getSession()?.status).toBe("anonymous");
    });

    // 실패를 삼키면 게스트가 체험이 끝났다고 믿지만 서버 세션은 남아 있다.
    it("로그아웃에_실패하면_알리고_세션을_유지한다", async () => {
      vi.mocked(authApi.logout).mockRejectedValue(new Error("network"));

      await confirmEndTrial();

      expect(await screen.findByRole("alert")).toBeInTheDocument();
      expect(screen.queryByText("랜딩 화면")).not.toBeInTheDocument();
      expect(getSession()?.status).toBe("authenticated");
    });
  });
});
