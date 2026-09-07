import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { requireSession, resetSession, setSession } from "../../lib/session";
import Profile, { clientLoader, clientAction } from "./profile";
import * as profileApi from "../../lib/api/profile";
import * as onboardingApi from "../../lib/onboardingApi";

describe("S-05 프로필 화면", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    // 세션 캐시는 모듈 전역이라 앞 테스트 값을 물려받을 수 있다 — 매번 지우고 화면이
    // 실제로 렌더되는 상황(온보딩 레이아웃 안, 세션 확정 후)과 같게 채운다. 이렇게 해야
    // clientAction 이 (콜드 캐시로) 실제 네트워크 fetch 를 시도하지 않고, 저장 성공 뒤
    // 세션 갱신 분기도 테스트가 실제로 지나간다.
    resetSession();
    setSession({
      status: "authenticated",
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "PROFILE",
    });
  });

  function renderScreen() {
    const Stub = createRoutesStub([
      {
        path: "/onboarding/profile",
        Component: Profile,
        loader: clientLoader,
        action: clientAction,
        HydrateFallback: () => null,
      },
      { path: "/onboarding/income", Component: () => <div>소득 화면</div> },
    ]);
    return render(<Stub initialEntries={["/onboarding/profile"]} />);
  }

  it("저장된 프로필을 초기값으로 보여준다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: "DATA",
      yearsOfExperience: 7,
      region: "부산",
      employmentType: "CONTRACT",
      companySize: null,
    });

    renderScreen();

    expect(await screen.findByLabelText("주 직무")).toHaveValue("DATA");
    expect(screen.getByLabelText("총 경력 (년)")).toHaveValue(7);
  });

  it("저장하면 프로필을 보내고 단계를 INCOME 으로 옮긴 뒤 소득 화면으로 간다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: null,
      yearsOfExperience: null,
      region: null,
      employmentType: null,
      companySize: null,
    });
    const patch = vi.spyOn(profileApi, "patchProfile").mockResolvedValue({
      jobFamily: "DEVELOPER",
      yearsOfExperience: 5,
      region: "서울",
      employmentType: "FULL_TIME",
      companySize: null,
    });
    const advance = vi
      .spyOn(onboardingApi, "advanceOnboarding")
      .mockResolvedValue({ currentStep: "INCOME", completed: false });

    renderScreen();
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText("주 직무"), "DEVELOPER");
    await user.type(screen.getByLabelText("총 경력 (년)"), "5");
    await user.selectOptions(screen.getByLabelText("희망 근무 지역"), "서울");
    await user.selectOptions(screen.getByLabelText("고용 형태"), "FULL_TIME");
    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText("소득 화면")).toBeInTheDocument();
    expect(patch).toHaveBeenCalled();
    expect(advance).toHaveBeenCalledWith("INCOME");
  });

  it("총 경력이 범위를 벗어나면 저장하지 않고 오류를 보여준다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: null,
      yearsOfExperience: null,
      region: null,
      employmentType: null,
      companySize: null,
    });
    const patch = vi.spyOn(profileApi, "patchProfile");

    renderScreen();
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText("주 직무"), "DEVELOPER");
    await user.type(screen.getByLabelText("총 경력 (년)"), "99");
    await user.selectOptions(screen.getByLabelText("희망 근무 지역"), "서울");
    await user.selectOptions(screen.getByLabelText("고용 형태"), "FULL_TIME");
    await user.click(screen.getByRole("button", { name: "다음" }));

    // 라벨("총 경력 (년)")도 같은 부분 문자열을 포함해 느슨한 정규식은 다중 매치로 깨진다
    // — 필드 에러 메시지 전체를 정확히 매칭한다.
    expect(
      await screen.findByText("총 경력은 0~50 사이의 정수로 입력해 주세요."),
    ).toBeInTheDocument();
    expect(patch).not.toHaveBeenCalled();
  });

  // 회귀 테스트 — guardOnboarding 은 세션 캐시의 onboardingStep 을 읽는다. "다음" 으로
  // 단계를 넘긴 뒤 이 캐시를 갱신하지 않으면, 온보딩 레이아웃 가드가 여전히 PROFILE 로
  // 보고 사용자를 이 화면으로 도로 튕긴다 — 무한 되튕김. patchProfile·advanceOnboarding
  // 이 끝난 뒤 setSession 으로 캐시를 갱신했는지 여기서 직접 확인한다.
  it("저장 성공 후 세션 캐시의 onboardingStep 을 INCOME 으로 갱신해 되튕김을 막는다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: null,
      yearsOfExperience: null,
      region: null,
      employmentType: null,
      companySize: null,
    });
    vi.spyOn(profileApi, "patchProfile").mockResolvedValue({
      jobFamily: "DEVELOPER",
      yearsOfExperience: 5,
      region: "서울",
      employmentType: "FULL_TIME",
      companySize: null,
    });
    vi.spyOn(onboardingApi, "advanceOnboarding").mockResolvedValue({
      currentStep: "INCOME",
      completed: false,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText("주 직무"), "DEVELOPER");
    await user.type(screen.getByLabelText("총 경력 (년)"), "5");
    await user.selectOptions(screen.getByLabelText("희망 근무 지역"), "서울");
    await user.selectOptions(screen.getByLabelText("고용 형태"), "FULL_TIME");
    await user.click(screen.getByRole("button", { name: "다음" }));

    await screen.findByText("소득 화면");

    await expect(requireSession()).resolves.toMatchObject({
      status: "authenticated",
      onboardingStep: "INCOME",
    });
  });

  it("저장 후 나가기는 부분 검증만 하고 저장됨 뱃지를 보여준다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: null,
      yearsOfExperience: null,
      region: null,
      employmentType: null,
      companySize: null,
    });
    const patch = vi.spyOn(profileApi, "patchProfile").mockResolvedValue({
      jobFamily: "DEVELOPER",
      yearsOfExperience: null,
      region: null,
      employmentType: null,
      companySize: null,
    });
    const advance = vi.spyOn(onboardingApi, "advanceOnboarding");

    renderScreen();
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText("주 직무"), "DEVELOPER");
    await user.click(screen.getByRole("button", { name: "저장 후 나가기" }));

    expect(await screen.findByText("저장됨")).toBeInTheDocument();
    expect(patch).toHaveBeenCalledWith({ jobFamily: "DEVELOPER" });
    // 부분 저장은 온보딩 단계를 넘기지 않는다 — "다음" 만 advanceOnboarding 을 부른다.
    expect(advance).not.toHaveBeenCalled();
  });
});
