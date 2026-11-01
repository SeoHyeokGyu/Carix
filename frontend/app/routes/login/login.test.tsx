import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as authApi from "../../lib/authApi";
import * as sessionLib from "../../lib/session";
import Login, { clientLoader, HydrateFallback } from "./login";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock("../../lib/authApi", async () => ({
  ...(await vi.importActual<typeof authApi>("../../lib/authApi")),
  login: vi.fn(),
}));

const GUEST_SESSION = {
  userId: "g-1",
  email: null,
  onboardingStep: "INCOME",
  guest: true,
};

/** 세션 조회(`/api/v1/auth/session`)만 응답한다. null 이면 401(비로그인). */
function stubSessionResponse(body: object | null) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () =>
      body
        ? new Response(JSON.stringify(body), { status: 200 })
        : new Response(null, { status: 401 }),
    ),
  );
}

function renderLogin(entry = "/login") {
  const Stub = createRoutesStub([
    { path: "/login", Component: Login, HydrateFallback, loader: clientLoader },
  ]);
  render(<Stub initialEntries={[entry]} />);
}

describe("로그인 라우트 (Login)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionLib.resetSession();
    stubSessionResponse(null);
  });

  it("로그인 성공 시 세션 캐시를 채우고 온보딩 완료 사용자를 대시보드로 이동시킨다", async () => {
    const setSessionSpy = vi.spyOn(sessionLib, "setSession");
    vi.mocked(authApi.login).mockResolvedValueOnce({
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "COMPLETED",
      guest: false,
    });

    renderLogin();

    await screen.findByLabelText("이메일");
    fireEvent.change(screen.getByLabelText("이메일"), {
      target: { value: "user@carix.kr" },
    });
    fireEvent.change(screen.getByLabelText(/비밀번호/), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith(
        "user@carix.kr",
        "password123",
      );
      expect(setSessionSpy).toHaveBeenCalledWith({
        status: "authenticated",
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "COMPLETED",
        guest: false,
      });
      expect(mockNavigate).toHaveBeenCalledWith("/dashboard", {
        replace: true,
      });
    });
  });

  it("온보딩 미완료 사용자는 해당 온보딩 단계 경로로 이동시킨다", async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "PROFILE",
      guest: false,
    });

    renderLogin();

    await screen.findByLabelText("이메일");
    fireEvent.change(screen.getByLabelText("이메일"), {
      target: { value: "user@carix.kr" },
    });
    fireEvent.change(screen.getByLabelText(/비밀번호/), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/onboarding/profile", {
        replace: true,
      });
    });
  });

  it("게스트_쿠키가_있으면_캐시가_비어도_경고한다", async () => {
    stubSessionResponse(GUEST_SESSION);

    renderLogin();

    expect(await screen.findByText(/이어지지 않습니다/)).toBeInTheDocument();
  });

  it("게스트_경고의_가입_링크는_돌아갈_경로를_유지한다", async () => {
    stubSessionResponse(GUEST_SESSION);

    renderLogin("/login?next=%2Fdashboard%2Fsimulator");

    const link = await screen.findByRole("link", { name: "가입해 주세요" });
    expect(link).toHaveAttribute(
      "href",
      "/register?next=%2Fdashboard%2Fsimulator",
    );
  });

  it("세션이_없으면_경고하지_않는다", async () => {
    stubSessionResponse(null);

    renderLogin();

    expect(await screen.findByLabelText("이메일")).toBeInTheDocument();
    expect(screen.queryByText(/이어지지 않습니다/)).not.toBeInTheDocument();
    // 만료된 게스트는 쿠키가 없어 경고를 못 본다 — 새로 체험하는 출구는 늘 있다.
    expect(screen.getByRole("link", { name: "새로 체험하기" })).toHaveAttribute(
      "href",
      "/",
    );
  });

  it("로그인_성공_후_안전한_next_로_돌아간다", async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "COMPLETED",
      guest: false,
    });

    renderLogin("/login?next=%2Fmypage%2Fprofile");

    await screen.findByLabelText("이메일");
    fireEvent.change(screen.getByLabelText("이메일"), {
      target: { value: "user@carix.kr" },
    });
    fireEvent.change(screen.getByLabelText(/비밀번호/), {
      target: { value: "password123" },
    });
    fireEvent.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/mypage/profile", {
        replace: true,
      });
    });
  });
});
