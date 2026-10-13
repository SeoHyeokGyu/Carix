import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import MypageProfile, { clientAction } from "./profile";
import * as authApi from "../../lib/authApi";
import * as onboardingApi from "../../lib/onboardingApi";
import * as sessionModule from "../../lib/session";

vi.mock("../../lib/authApi", () => ({
  deleteAccount: vi.fn(),
  logoutAll: vi.fn(),
}));

vi.mock("../../lib/onboardingApi", () => ({
  resetOnboarding: vi.fn(),
}));

vi.mock("../../lib/session", () => ({
  getSession: vi.fn(),
  requireSession: vi.fn(),
  setSession: vi.fn(),
}));

describe("S-13 MypageProfile 라우트", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(sessionModule.getSession).mockReturnValue({
      status: "authenticated",
      userId: "u-123",
      email: "user@example.com",
      onboardingStep: "COMPLETED",
    });
    vi.mocked(sessionModule.requireSession).mockResolvedValue({
      status: "authenticated",
      userId: "u-123",
      email: "user@example.com",
      onboardingStep: "COMPLETED",
    });
  });

  function renderScreen() {
    const Stub = createRoutesStub([
      {
        path: "/mypage/profile",
        Component: MypageProfile,
        action: clientAction,
      },
      { path: "/login", Component: () => <div>로그인 화면</div> },
      {
        path: "/onboarding/profile",
        Component: () => <div>온보딩 프로필</div>,
      },
    ]);
    return render(<Stub initialEntries={["/mypage/profile"]} />);
  }

  it("설정 화면의 세 섹션(세션 관리, 온보딩 재설정, 회원 탈퇴)을 정상 렌더링한다", async () => {
    renderScreen();

    expect(screen.queryByText("S-13")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 1, name: "설정" }),
    ).toBeInTheDocument();
    expect(screen.getByText("세션 관리")).toBeInTheDocument();
    expect(screen.getByText("온보딩 재설정")).toBeInTheDocument();
    expect(
      screen.getByText("회원 탈퇴 및 개인 데이터 완전 삭제"),
    ).toBeInTheDocument();
    expect(await screen.findByText(/user@example.com/)).toBeInTheDocument();
  });

  it("동의 체크박스와 이메일 입력 전에는 탈퇴 버튼이 비활성화된다", async () => {
    renderScreen();

    const deleteBtn = screen.getByRole("button", {
      name: "회원 탈퇴 및 모든 데이터 삭제",
    });
    expect(deleteBtn).toBeDisabled();

    // 이메일만 입력했을 때 여전히 비활성화
    const emailInput = screen.getByLabelText(
      /탈퇴 확인을 위해 이메일 주소를 입력해 주세요/,
    );
    fireEvent.change(emailInput, { target: { value: "user@example.com" } });
    expect(deleteBtn).toBeDisabled();

    // 체크박스 체크 시 활성화
    const checkbox = screen.getByLabelText(
      /안내 사항을 모두 확인하였으며, 데이터 영구 삭제에 동의합니다/,
    );
    fireEvent.click(checkbox);
    expect(deleteBtn).not.toBeDisabled();
  });

  describe("clientAction", () => {
    it("logout-all 액션 시 logoutAll을 호출하고 로그인 페이지로 리다이렉트한다", async () => {
      vi.mocked(authApi.logoutAll).mockResolvedValue();

      const formData = new FormData();
      formData.set("intent", "logout-all");

      const request = new Request("http://localhost/mypage/profile", {
        method: "POST",
        body: formData,
      });

      const response = await clientAction({ request });
      expect(authApi.logoutAll).toHaveBeenCalledTimes(1);
      expect(sessionModule.setSession).toHaveBeenCalledWith({
        status: "anonymous",
      });
      expect(response).toBeInstanceOf(Response);
      expect((response as Response).headers.get("Location")).toBe("/login");
    });

    it("delete-account 액션 시 이메일이 일치하면 deleteAccount 및 logoutAll을 호출하고 리다이렉트한다", async () => {
      vi.mocked(authApi.deleteAccount).mockResolvedValue();
      vi.mocked(authApi.logoutAll).mockResolvedValue();

      const formData = new FormData();
      formData.set("intent", "delete-account");
      formData.set("email", "user@example.com");

      const request = new Request("http://localhost/mypage/profile", {
        method: "POST",
        body: formData,
      });

      const response = await clientAction({ request });
      expect(authApi.deleteAccount).toHaveBeenCalledWith("user@example.com");
      expect(authApi.logoutAll).toHaveBeenCalledTimes(1);
      expect(sessionModule.setSession).toHaveBeenCalledWith({
        status: "anonymous",
      });
      expect(response).toBeInstanceOf(Response);
      expect((response as Response).headers.get("Location")).toBe(
        "/login?deleted=true",
      );
    });

    it("delete-account 액션 시 이메일이 누락되면 에러를 반환한다", async () => {
      const formData = new FormData();
      formData.set("intent", "delete-account");
      formData.set("email", "");

      const request = new Request("http://localhost/mypage/profile", {
        method: "POST",
        body: formData,
      });

      const result = await clientAction({ request });
      expect(result).toEqual({
        error: "계정 삭제를 확인하기 위해 이메일을 입력해 주세요.",
        intent: "delete-account",
      });
      expect(authApi.deleteAccount).not.toHaveBeenCalled();
    });

    it("reset-onboarding 액션 시 resetOnboarding을 호출하고 온보딩으로 이동한다", async () => {
      vi.mocked(onboardingApi.resetOnboarding).mockResolvedValue({
        currentStep: "PROFILE",
        completed: false,
      });
      vi.mocked(sessionModule.requireSession).mockResolvedValue({
        status: "authenticated",
        userId: "u-123",
        email: "user@example.com",
        onboardingStep: "COMPLETED",
      });

      const formData = new FormData();
      formData.set("intent", "reset-onboarding");

      const request = new Request("http://localhost/mypage/profile", {
        method: "POST",
        body: formData,
      });

      const response = await clientAction({ request });
      expect(onboardingApi.resetOnboarding).toHaveBeenCalledTimes(1);
      expect(response).toBeInstanceOf(Response);
      expect((response as Response).headers.get("Location")).toBe(
        "/onboarding/profile",
      );
    });
  });
});
