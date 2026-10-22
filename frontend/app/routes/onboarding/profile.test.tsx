import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as profileApi from "../../lib/api/profile";
import { UnauthorizedError } from "../../lib/coreApi";
import * as onboardingApi from "../../lib/onboardingApi";
import { requireSession, resetSession, setSession } from "../../lib/session";
import Profile, { clientAction, clientLoader } from "./profile";

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

  function renderScreen(initialEntry = "/onboarding/profile") {
    const Stub = createRoutesStub([
      {
        path: "/onboarding/profile",
        Component: Profile,
        loader: clientLoader,
        action: clientAction,
        HydrateFallback: () => null,
      },
      { path: "/onboarding/income", Component: () => <div>소득 화면</div> },
      { path: "/onboarding/review", Component: () => <div>검토 화면</div> },
      { path: "/", Component: () => <div>랜딩 페이지</div> },
      { path: "/login", Component: () => <div>로그인 화면</div> },
    ]);
    return render(<Stub initialEntries={[initialEntry]} />);
  }

  it("저장하면 프로필을 보내고 단계를 INCOME 으로 옮긴 뒤 소득 화면으로 간다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: null,
      yearsOfExperience: null,
      region: null,
      employmentType: null,
      companySize: null,
      hireDate: null,
    });
    const patch = vi.spyOn(profileApi, "patchProfile").mockResolvedValue({
      jobFamily: "DEVELOPER",
      yearsOfExperience: 5,
      region: "서울",
      employmentType: "FULL_TIME",
      companySize: null,
      hireDate: null,
    });
    const advance = vi
      .spyOn(onboardingApi, "advanceOnboarding")
      .mockResolvedValue({ currentStep: "INCOME", completed: false });

    renderScreen();
    const user = userEvent.setup();

    await user.selectOptions(
      await screen.findByLabelText("주 직무"),
      "DEVELOPER",
    );
    await user.type(screen.getByLabelText("총 경력 (년)"), "5");
    await user.selectOptions(screen.getByLabelText("희망 근무 지역"), "서울");
    await user.selectOptions(screen.getByLabelText("고용 형태"), "FULL_TIME");
    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText("소득 화면")).toBeInTheDocument();
    // 호출 여부만으로는 필드 하나가 누락되거나 잘못된 키로 나가도 통과할 수 있다
    // — 실제로 보낸 페이로드를 직접 단언한다(companySize 는 선택 필드라 고르지
    // 않으면 키 자체가 없다, formData.ts 의 "빈 문자열은 키를 안 만든다" 규칙).
    expect(patch).toHaveBeenCalledWith({
      jobFamily: "DEVELOPER",
      yearsOfExperience: 5,
      region: "서울",
      employmentType: "FULL_TIME",
    });
    expect(advance).toHaveBeenCalledWith("INCOME");
  });

  it("총 경력이 범위를 벗어나면 저장하지 않고 오류를 보여준다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: null,
      yearsOfExperience: null,
      region: null,
      employmentType: null,
      companySize: null,
      hireDate: null,
    });
    const patch = vi.spyOn(profileApi, "patchProfile");

    renderScreen();
    const user = userEvent.setup();

    await user.selectOptions(
      await screen.findByLabelText("주 직무"),
      "DEVELOPER",
    );
    await user.type(screen.getByLabelText("총 경력 (년)"), "99");
    await user.selectOptions(screen.getByLabelText("희망 근무 지역"), "서울");
    await user.selectOptions(screen.getByLabelText("고용 형태"), "FULL_TIME");
    await user.click(screen.getByRole("button", { name: "다음" }));

    // 라벨("총 경력 (년)")도 같은 부분 문자열을 포함해 느슨한 정규식은 다중 매치로 깨진다
    // — 필드 에러 메시지 전체를 정확히 매칭한다.
    const errorText = await screen.findByText(
      "총 경력은 0~50 사이의 숫자로 입력해 주세요.",
    );
    expect(errorText).toBeInTheDocument();
    expect(patch).not.toHaveBeenCalled();

    // 접근성 배선 — 입력이 자신의 오류 문단을 aria-describedby 로 가리키고, 실패한
    // 제출 뒤 포커스가 그 입력(첫 번째이자 유일한 잘못된 필드)으로 옮겨져야 한다.
    const invalidField = screen.getByLabelText("총 경력 (년)");
    expect(invalidField).toHaveAttribute("aria-describedby", errorText.id);
    expect(invalidField).toHaveFocus();
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
      hireDate: null,
    });
    vi.spyOn(profileApi, "patchProfile").mockResolvedValue({
      jobFamily: "DEVELOPER",
      yearsOfExperience: 5,
      region: "서울",
      employmentType: "FULL_TIME",
      companySize: null,
      hireDate: null,
    });
    vi.spyOn(onboardingApi, "advanceOnboarding").mockResolvedValue({
      currentStep: "INCOME",
      completed: false,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.selectOptions(
      await screen.findByLabelText("주 직무"),
      "DEVELOPER",
    );
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

  // FIX 1 (Critical, 최종 리뷰) — 검토(REVIEW) 화면의 "수정" 링크로 돌아와 프로필을
  // 고친 사용자가 "다음"을 다시 누르면, 예전에는 advanceOnboarding("INCOME") 을
  // 무조건 다시 불러 서버 단계를 REVIEW 에서 INCOME 으로 되돌렸다. 이어서 소득→소비
  // 화면에서도 같은 실수가 반복되면 결국 화면 자체가 없는 SKILLS 단계로 사용자가
  // 떠밀려 어디로도 못 가는 루프에 갇힌다("최종 리뷰" 보고서 참고). "다음"은 여전히
  // 소득 화면으로 데려가고 patchProfile 은 그대로 불러야 한다(이미 지난 단계를
  // 고치는 것 자체는 정당한 흐름이다) — 다만 이미 지나온 단계를 다시 서버에 적어
  // 넣지는 않아야 한다.
  it("이미 REVIEW 단계를 지난 사용자가 프로필을 고치고 '다음'을 눌러도 온보딩 단계를 되돌리지 않는다", async () => {
    resetSession();
    setSession({
      status: "authenticated",
      userId: "u-1",
      email: "user@carix.kr",
      onboardingStep: "REVIEW",
    });
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: "DEVELOPER",
      yearsOfExperience: 5,
      region: "서울",
      employmentType: "FULL_TIME",
      companySize: null,
      hireDate: null,
    });
    const patch = vi.spyOn(profileApi, "patchProfile").mockResolvedValue({
      jobFamily: "DEVELOPER",
      yearsOfExperience: 6,
      region: "서울",
      employmentType: "FULL_TIME",
      companySize: null,
      hireDate: null,
    });
    // 성공값을 준다 — 목킹 없이 두면 가드가 사라졌을 때 이 스파이가 실제
    // advanceOnboarding(진짜 fetch)로 새 나가 요청이 걸린 채 타임아웃으로
    // 실패한다. 그러면 이 테스트는 "advance 가 불렸다"가 아니라 "요청이 안
    // 끝났다"로 우연히 초록불이 아닌 빨간불이 되는 것이라, 다음에 누군가 이
    // 스위트에 advanceOnboarding 목을 하나 추가하기만 해도(자연스러운 일이다)
    // 타임아웃이 사라지고 가드 삭제가 다시 조용히 통과할 수 있다. 실패
    // 지점을 `expect(advance).not.toHaveBeenCalled()` 자체로 고정한다.
    const advance = vi
      .spyOn(onboardingApi, "advanceOnboarding")
      .mockResolvedValue({ currentStep: "INCOME", completed: false });

    renderScreen();
    const user = userEvent.setup();

    const experienceInput = await screen.findByLabelText("총 경력 (년)");
    await user.clear(experienceInput);
    await user.type(experienceInput, "6");
    await user.click(screen.getByRole("button", { name: "다음" }));

    // "다음"은 여전히 다음 화면으로 데려가고 프로필은 그대로 저장한다 — 막히는 건
    // 서버 단계 갱신뿐이다.
    expect(await screen.findByText("소득 화면")).toBeInTheDocument();
    expect(patch).toHaveBeenCalled();
    expect(advance).not.toHaveBeenCalled();

    await expect(requireSession()).resolves.toMatchObject({
      status: "authenticated",
      onboardingStep: "REVIEW",
    });
  });

  it("검토 화면의 「수정」으로 들어왔으면 저장 후 검토 화면으로 돌아간다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: "DEVELOPER",
      yearsOfExperience: 5,
      region: "서울",
      employmentType: "FULL_TIME",
      companySize: null,
      hireDate: null,
    });
    const patch = vi.spyOn(profileApi, "patchProfile").mockResolvedValue({
      jobFamily: "DEVELOPER",
      yearsOfExperience: 6,
      region: "서울",
      employmentType: "FULL_TIME",
      companySize: null,
      hireDate: null,
    });
    vi.spyOn(onboardingApi, "advanceOnboarding").mockResolvedValue({
      currentStep: "INCOME",
      completed: false,
    });

    renderScreen("/onboarding/profile?from=review");
    const user = userEvent.setup();

    await screen.findByLabelText("총 경력 (년)");
    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText("검토 화면")).toBeInTheDocument();
    expect(patch).toHaveBeenCalled();
  });

  it("저장 후 나가기는 부분 검증만 하고 랜딩 페이지로 이동한다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: null,
      yearsOfExperience: null,
      region: null,
      employmentType: null,
      companySize: null,
      hireDate: null,
    });
    const patch = vi.spyOn(profileApi, "patchProfile").mockResolvedValue({
      jobFamily: "DEVELOPER",
      yearsOfExperience: null,
      region: null,
      employmentType: null,
      companySize: null,
      hireDate: null,
    });
    const advance = vi.spyOn(onboardingApi, "advanceOnboarding");

    renderScreen();
    const user = userEvent.setup();

    await user.selectOptions(
      await screen.findByLabelText("주 직무"),
      "DEVELOPER",
    );
    await user.click(screen.getByRole("button", { name: "저장 후 나가기" }));

    // 목적지는 랜딩(`/`) 뿐이다 — 온보딩 미완료 상태에서는 /dashboard 로 가도
    // guardOnboarded 가 다시 현재 단계로 돌려보내므로 갈 이유가 없다.
    expect(await screen.findByText("랜딩 페이지")).toBeInTheDocument();
    expect(patch).toHaveBeenCalledWith({ jobFamily: "DEVELOPER" });
    // 부분 저장은 온보딩 단계를 넘기지 않는다 — "다음" 만 advanceOnboarding 을 부른다.
    expect(advance).not.toHaveBeenCalled();
  });

  // 회귀 테스트 — "다음" 분기의 setSession 을 이 분기에 그대로 옮겨 붙이는 실수를
  // 잡는다. "저장 후 나가기"는 사용자가 지금 단계에서 멈추는 것이므로, 세션 캐시의
  // onboardingStep 은 그대로 PROFILE 이어야 한다 — 그래야 나중에 다시 로그인했을 때
  // guardOnboarding 이 이 화면으로 돌려보낸다.
  it("저장 후 나가기는 세션 캐시의 onboardingStep 을 바꾸지 않는다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: null,
      yearsOfExperience: null,
      region: null,
      employmentType: null,
      companySize: null,
      hireDate: null,
    });
    vi.spyOn(profileApi, "patchProfile").mockResolvedValue({
      jobFamily: "DEVELOPER",
      yearsOfExperience: null,
      region: null,
      employmentType: null,
      companySize: null,
      hireDate: null,
    });

    renderScreen();
    const user = userEvent.setup();

    await user.selectOptions(
      await screen.findByLabelText("주 직무"),
      "DEVELOPER",
    );
    await user.click(screen.getByRole("button", { name: "저장 후 나가기" }));

    await screen.findByText("랜딩 페이지");

    await expect(requireSession()).resolves.toMatchObject({
      status: "authenticated",
      onboardingStep: "PROFILE",
    });
  });

  it("조회 중 401 을 받으면 로그인 화면으로 보낸다", async () => {
    vi.spyOn(profileApi, "getProfile").mockRejectedValue(
      new UnauthorizedError(),
    );

    renderScreen();

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });
});
