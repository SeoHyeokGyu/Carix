import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as authApi from "../../lib/authApi";
import * as sessionLib from "../../lib/session";
import Register, { clientLoader, HydrateFallback } from "./register";

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
  register: vi.fn(),
}));

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

function renderRegister(entry = "/register") {
  const Stub = createRoutesStub([
    {
      path: "/register",
      Component: Register,
      HydrateFallback,
      loader: clientLoader,
    },
  ]);
  render(<Stub initialEntries={[entry]} />);
}

async function submit(email: string) {
  await screen.findByLabelText("이메일");
  fireEvent.change(screen.getByLabelText("이메일"), {
    target: { value: email },
  });
  fireEvent.change(screen.getByLabelText(/비밀번호/), {
    target: { value: "password123" },
  });
  fireEvent.click(screen.getByRole("button", { name: "계정 만들기" }));
}

describe("회원가입 라우트 (Register)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionLib.resetSession();
    stubSessionResponse(null);
  });

  it("가입 성공 시 세션 캐시를 채우고 온보딩 단계 경로로 이동시킨다", async () => {
    const setSessionSpy = vi.spyOn(sessionLib, "setSession");
    vi.mocked(authApi.register).mockResolvedValueOnce({
      userId: "u-10",
      email: "newuser@carix.kr",
      onboardingStep: "PROFILE",
      guest: false,
    });

    renderRegister();
    await submit("newuser@carix.kr");

    await waitFor(() => {
      expect(authApi.register).toHaveBeenCalledWith(
        "newuser@carix.kr",
        "password123",
      );
      expect(setSessionSpy).toHaveBeenCalledWith({
        status: "authenticated",
        userId: "u-10",
        email: "newuser@carix.kr",
        onboardingStep: "PROFILE",
        guest: false,
      });
      expect(mockNavigate).toHaveBeenCalledWith("/onboarding/profile", {
        replace: true,
      });
    });
  });

  it("가입_후_안전한_next_가_있으면_그_경로로_돌아간다", async () => {
    vi.mocked(authApi.register).mockResolvedValueOnce({
      userId: "g-1",
      email: "newuser@carix.kr",
      onboardingStep: "INCOME",
      guest: false,
    });

    renderRegister("/register?next=%2Fdashboard%2Fsimulator");
    await submit("newuser@carix.kr");

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/dashboard/simulator", {
        replace: true,
      });
    });
  });

  it("외부_주소_next_는_무시하고_온보딩_단계로_간다", async () => {
    vi.mocked(authApi.register).mockResolvedValueOnce({
      userId: "u-10",
      email: "newuser@carix.kr",
      onboardingStep: "PROFILE",
      guest: false,
    });

    renderRegister("/register?next=%2F%2Fevil.com");
    await submit("newuser@carix.kr");

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/onboarding/profile", {
        replace: true,
      });
    });
  });

  it("게스트가_중복_이메일이면_로그인_시_유실을_안내한다", async () => {
    stubSessionResponse({
      userId: "g-1",
      email: null,
      onboardingStep: "INCOME",
      guest: true,
    });
    vi.mocked(authApi.register).mockRejectedValueOnce({
      code: "EMAIL_TAKEN",
      message: "이미 가입된 이메일입니다.",
    });

    renderRegister("/register?next=%2Fdashboard");
    await submit("taken@carix.kr");

    expect(
      await screen.findByText(/체험 중 입력한 내용은 이어지지 않습니다/),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "로그인하기" })).toHaveAttribute(
      "href",
      "/login?next=%2Fdashboard",
    );
  });
});
