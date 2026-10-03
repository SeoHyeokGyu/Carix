import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { AccountMenu } from "./AccountMenu";

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
  it("열면 설정 링크와 로그아웃 항목을 보인다", async () => {
    renderAt("/dashboard");
    await openMenu();

    expect(
      await screen.findByRole("menuitem", { name: "설정" }),
    ).toHaveAttribute("href", "/mypage/profile");
    expect(
      screen.getByRole("menuitem", { name: "로그아웃" }),
    ).toBeInTheDocument();
  });

  it("입력 관리는 주 메뉴에 남으므로 계정 메뉴에 없다", async () => {
    renderAt("/dashboard");
    await openMenu();

    await screen.findByRole("menuitem", { name: "설정" });
    expect(
      screen.queryByRole("menuitem", { name: "입력 관리" }),
    ).not.toBeInTheDocument();
  });

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

  it("설정 화면에서는 트리거가 활성 상태다", async () => {
    renderAt("/mypage/profile");

    expect(
      await screen.findByRole("button", { name: "내 정보" }),
    ).toHaveAttribute("data-active", "true");
  });

  it("다른 화면에서는 트리거가 활성 상태가 아니다", async () => {
    renderAt("/dashboard");

    expect(
      await screen.findByRole("button", { name: "내 정보" }),
    ).toHaveAttribute("data-active", "false");
  });
});
