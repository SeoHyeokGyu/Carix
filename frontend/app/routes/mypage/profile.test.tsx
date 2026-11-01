import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as profileApi from "../../lib/api/profile";
import * as authApi from "../../lib/authApi";
import * as sessionModule from "../../lib/session";
import MypageProfile, { clientAction, clientLoader } from "./profile";

vi.mock("../../lib/api/profile", () => ({
  getProfile: vi.fn(),
  patchProfile: vi.fn(),
}));

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
      guest: false,
    });
    vi.mocked(sessionModule.requireSession).mockResolvedValue({
      status: "authenticated",
      userId: "u-123",
      email: "user@example.com",
      onboardingStep: "COMPLETED",
      guest: false,
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
  });

  describe("입사일", () => {
    function renderWithLoader() {
      const Stub = createRoutesStub([
        {
          path: "/mypage/profile",
          Component: MypageProfile,
          action: clientAction,
          loader: clientLoader,
          HydrateFallback: () => null,
        },
        { path: "/login", Component: () => <div>로그인 화면</div> },
      ]);
      return render(<Stub initialEntries={["/mypage/profile"]} />);
    }

    beforeEach(() => {
      vi.mocked(profileApi.getProfile).mockResolvedValue({
        jobFamily: "DEVELOPER",
        yearsOfExperience: 3,
        region: "서울",
        employmentType: "FULL_TIME",
        companySize: null,
        hireDate: "2022-03-02",
      });
    });

    it("입사일을 저장하면 hireDate 만 보낸다", async () => {
      vi.mocked(profileApi.patchProfile).mockResolvedValue({
        jobFamily: "DEVELOPER",
        yearsOfExperience: 3,
        region: "서울",
        employmentType: "FULL_TIME",
        companySize: null,
        hireDate: "2023-05-01",
      });
      renderWithLoader();
      const user = userEvent.setup();

      const input = await screen.findByLabelText("입사일");
      fireEvent.change(input, { target: { value: "2023-05-01" } });
      await user.click(screen.getByRole("button", { name: "입사일 저장" }));

      await waitFor(() =>
        expect(profileApi.patchProfile).toHaveBeenCalledWith({
          hireDate: "2023-05-01",
        }),
      );
      expect(
        await screen.findByText("입사일을 저장했습니다."),
      ).toBeInTheDocument();
    });

    it("서버가 거부하면 메시지를 보여준다", async () => {
      vi.mocked(profileApi.patchProfile).mockRejectedValue({
        code: "INVALID_INPUT",
        message: "입사일은 오늘 이후일 수 없습니다.",
        status: 400,
      });
      renderWithLoader();
      const user = userEvent.setup();

      await screen.findByLabelText("입사일");
      await user.click(screen.getByRole("button", { name: "입사일 저장" }));

      expect(
        await screen.findByText("입사일은 오늘 이후일 수 없습니다."),
      ).toBeInTheDocument();
    });

    it("입사일 조회가 실패하면 알리고 저장을 막는다", async () => {
      const spy = vi.spyOn(console, "error").mockImplementation(() => {});
      vi.mocked(profileApi.getProfile).mockRejectedValue({
        code: "UNKNOWN",
        message: "x",
        status: 500,
      });
      renderWithLoader();

      expect(
        await screen.findByText(/저장된 입사일을 불러오지 못했습니다/),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "입사일 저장" }),
      ).toBeDisabled();
      expect(spy).toHaveBeenCalled();
      spy.mockRestore();
    });

    it("입사일이 비어 있으면 서버를 부르지 않는다", async () => {
      const formData = new FormData();
      formData.set("intent", "save-hire-date");
      formData.set("hireDate", "");
      const request = new Request("http://localhost/mypage/profile", {
        method: "POST",
        body: formData,
      });

      const result = await clientAction({ request });

      expect(result).toEqual({
        error: "입사일을 선택해 주세요.",
        intent: "save-hire-date",
      });
      expect(profileApi.patchProfile).not.toHaveBeenCalled();
    });
  });
});
