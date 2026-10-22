import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub, MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { UnauthorizedError } from "../../lib/coreApi";
import * as skillApi from "../../lib/skillApi";
import { confirmDialog } from "../../test-utils/confirmDialog";
import Skills from "./skills";

vi.mock("../../lib/skillApi", () => ({
  fetchUserSkills: vi.fn(),
  searchSkills: vi.fn(),
  createUserSkill: vi.fn(),
  updateUserSkill: vi.fn(),
  deleteUserSkill: vi.fn(),
}));

describe("S-08 Skills 온보딩 화면", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(skillApi.fetchUserSkills).mockResolvedValue([
      {
        id: "u-1",
        skillId: "s-1",
        standardName: "Spring Boot",
        isCustom: false,
        monthsOfExperience: 36,
        lastUsedDate: "2026-08-01",
      },
      {
        id: "u-2",
        skillId: "s-2",
        standardName: "TypeScript",
        isCustom: false,
        monthsOfExperience: 18,
        lastUsedDate: "2026-07-15",
      },
    ]);

    vi.mocked(skillApi.searchSkills).mockResolvedValue([
      { id: "s-3", standardName: "Docker", isCustom: false },
      { id: "s-4", standardName: "Kubernetes", isCustom: false },
    ]);
  });

  it("스킬 검색 후 선택하여 새 스킬을 등록할 수 있다", async () => {
    vi.mocked(skillApi.createUserSkill).mockResolvedValue({
      id: "u-3",
      skillId: "s-3",
      standardName: "Docker",
      isCustom: false,
      monthsOfExperience: 24,
      lastUsedDate: "2026-08-01",
    });

    render(
      <MemoryRouter>
        <Skills />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("Spring Boot")).toBeInTheDocument();
    });

    // 1. 검색어 입력
    const searchInput = screen.getByPlaceholderText(/스킬 이름을 검색하세요/i);
    fireEvent.change(searchInput, { target: { value: "Doc" } });

    // 2. 검색 결과 드롭다운 표시 대기
    await waitFor(() => {
      expect(
        screen.getByRole("option", { name: "Docker" }),
      ).toBeInTheDocument();
    });

    // 3. 스킬 선택
    fireEvent.click(screen.getByRole("option", { name: "Docker" }));

    // 4. 입력 폼에 값 입력
    const monthsInput = screen.getByLabelText(/실무 사용 기간/i);
    fireEvent.change(monthsInput, { target: { value: "24" } });

    // 5. 추가 버튼 클릭
    const submitBtn = screen.getByRole("button", { name: "+ 스킬 추가" });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(skillApi.createUserSkill).toHaveBeenCalledWith(
        expect.objectContaining({
          standardName: "Docker",
          monthsOfExperience: 24,
        }),
      );
      expect(
        screen.getByText(/'Docker' 스킬이 등록되었습니다/i),
      ).toBeInTheDocument();
    });
  });

  it("동일한 스킬 중복 등록 시도 시 에러 메시지를 표시한다", async () => {
    render(
      <MemoryRouter>
        <Skills />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("Spring Boot")).toBeInTheDocument();
    });

    vi.mocked(skillApi.searchSkills).mockResolvedValueOnce([
      { id: "s-1", standardName: "Spring Boot", isCustom: false },
    ]);

    const searchInput = screen.getByPlaceholderText(/스킬 이름을 검색하세요/i);
    fireEvent.change(searchInput, { target: { value: "Spring" } });

    await waitFor(() => {
      expect(
        screen.getByRole("option", { name: "Spring Boot" }),
      ).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("option", { name: "Spring Boot" }));

    const monthsInput = screen.getByLabelText(/실무 사용 기간/i);
    fireEvent.change(monthsInput, { target: { value: "12" } });

    const submitBtn = screen.getByRole("button", { name: "+ 스킬 추가" });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(
        screen.getByText("이미 등록되어 있는 스킬입니다."),
      ).toBeInTheDocument();
      expect(skillApi.createUserSkill).not.toHaveBeenCalled();
    });

    // 같은 실패를 다시 내면 알림을 새로 그린다 — 같은 노드에 같은 문장이면 스크린리더가 다시 읽지 않는다.
    const first = screen.getByRole("alert");
    fireEvent.click(submitBtn);
    await waitFor(() => expect(screen.getByRole("alert")).not.toBe(first));
    expect(screen.getByRole("alert")).toHaveTextContent(
      "이미 등록되어 있는 스킬입니다.",
    );
  });

  it("사전에 없는 스킬 직접 추가 시 사용자 정의 스킬로 안내하고 카테고리를 지원한다", async () => {
    vi.mocked(skillApi.searchSkills).mockResolvedValueOnce([]);
    vi.mocked(skillApi.createUserSkill).mockResolvedValueOnce({
      id: "u-99",
      skillId: "s-99",
      standardName: "CustomLang",
      isCustom: true,
      monthsOfExperience: 6,
      lastUsedDate: "2026-08-01",
    });

    render(
      <MemoryRouter>
        <Skills />
      </MemoryRouter>,
    );

    const searchInput = screen.getByPlaceholderText(/스킬 이름을 검색하세요/i);
    fireEvent.change(searchInput, { target: { value: "CustomLang" } });

    await waitFor(() => {
      expect(
        screen.getByText(/사전에 없는 스킬로 직접 추가하기/i),
      ).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText(/사전에 없는 스킬로 직접 추가하기/i));

    // 사용자 정의 안내 메시지 및 카테고리 선택란 확인
    expect(
      screen.getByText(/시장 수요 통계가 제공되지 않을 수 있습니다/i),
    ).toBeInTheDocument();
    // 온보딩 선택 필드는 NativeSelect — 빈 선택지가 생기므로 required 로 막는다.
    const category = screen.getByLabelText(/직무 카테고리/i);
    expect(category).toBeRequired();
    expect(category).toHaveValue("BACKEND");

    const monthsInput = screen.getByLabelText(/실무 사용 기간/i);
    fireEvent.change(monthsInput, { target: { value: "6" } });

    fireEvent.click(screen.getByRole("button", { name: "+ 스킬 추가" }));

    await waitFor(() => {
      expect(skillApi.createUserSkill).toHaveBeenCalledWith(
        expect.objectContaining({
          standardName: "CustomLang",
          monthsOfExperience: 6,
          category: "BACKEND",
        }),
      );
    });
  });

  it("스킬 삭제 시 deleteUserSkill을 호출하고 목록에서 제거된다", async () => {
    vi.mocked(skillApi.deleteUserSkill).mockResolvedValue();

    render(
      <MemoryRouter>
        <Skills />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("Spring Boot")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.click(screen.getAllByRole("button", { name: "삭제" })[0]);
    await confirmDialog(user, "삭제");

    await waitFor(() => {
      expect(skillApi.deleteUserSkill).toHaveBeenCalledWith("u-1");
      expect(
        screen.getByText(/'Spring Boot' 스킬이 삭제되었습니다/i),
      ).toBeInTheDocument();
    });
  });

  it("스킬 인라인 수정 후 저장이 정상 작동한다", async () => {
    vi.mocked(skillApi.updateUserSkill).mockResolvedValue({
      id: "u-1",
      skillId: "s-1",
      standardName: "Spring Boot",
      isCustom: false,
      monthsOfExperience: 48,
      lastUsedDate: "2026-08-01",
    });

    render(
      <MemoryRouter>
        <Skills />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("Spring Boot")).toBeInTheDocument();
    });

    const editButtons = screen.getAllByRole("button", { name: "수정" });
    fireEvent.click(editButtons[0]);

    // 수정 입력란 표시
    const saveButton = screen.getByRole("button", { name: "저장" });
    expect(saveButton).toBeInTheDocument();

    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(skillApi.updateUserSkill).toHaveBeenCalledWith(
        "u-1",
        expect.objectContaining({
          monthsOfExperience: 36,
        }),
      );
      expect(
        screen.getByText("스킬 정보가 수정되었습니다."),
      ).toBeInTheDocument();
    });
  });

  it("등록된 스킬이 0개일 때 다음(검토) 버튼을 누르면 에러 메시지를 표시한다", async () => {
    vi.mocked(skillApi.fetchUserSkills).mockResolvedValue([]);

    render(
      <MemoryRouter>
        <Skills />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("empty-state")).toHaveTextContent(
        "등록된 보유 스킬이 없습니다.",
      );
    });

    // 스킬이 없어도 「다음」은 눌린다 — 누르면 이유를 알린다.
    const nextBtn = screen.getByRole("button", { name: "다음" });
    expect(nextBtn).toBeEnabled();
    fireEvent.click(nextBtn);

    // 이유는 누른 버튼 바로 위에 뜬다 — 페이지 맨 위 알림은 화면 밖일 수 있다.
    const reason = screen.getByText(
      "최소 1개 이상의 보유 스킬을 등록해야 합니다.",
    );
    expect(reason).toHaveAttribute("role", "alert");
    expect(reason.nextElementSibling).toBe(nextBtn.parentElement);
  });

  it("동작 실패 문장은 목록을 덮지 않는 한 줄 알림이고 닫을 수 있다", async () => {
    vi.mocked(skillApi.deleteUserSkill).mockRejectedValue({
      code: "UNKNOWN",
      message: "삭제하지 못했습니다.",
      status: 500,
    });
    render(
      <MemoryRouter>
        <Skills />
      </MemoryRouter>,
    );
    const user = userEvent.setup();
    await screen.findByText("Spring Boot");
    // 삭제 실패(:261 의 setErrorMessage) — 동작 실패 경로
    await user.click(screen.getAllByRole("button", { name: "삭제" })[0]);
    await confirmDialog(user, "삭제");
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("삭제하지 못했습니다.");
    expect(screen.getByText("Spring Boot")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "알림 닫기" }));
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("스킬 목록 조회 중 401 을 받으면 로그인 화면으로 보낸다", async () => {
    vi.mocked(skillApi.fetchUserSkills).mockRejectedValue(
      new UnauthorizedError(),
    );

    const Stub = createRoutesStub([
      { path: "/onboarding/skills", Component: Skills },
      { path: "/login", Component: () => <div>로그인 화면</div> },
    ]);
    render(<Stub initialEntries={["/onboarding/skills"]} />);

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });

  it("목록 불러오기에 실패하면 목록 자리에 다시 시도를 두고, 성공하면 목록이 뜬다", async () => {
    const loaded = await skillApi.fetchUserSkills(); // beforeEach 의 기본 목 목록
    vi.mocked(skillApi.fetchUserSkills)
      .mockRejectedValueOnce(new Error("x"))
      .mockResolvedValueOnce(loaded);
    render(
      <MemoryRouter>
        <Skills />
      </MemoryRouter>,
    );
    const user = userEvent.setup();

    expect(
      await screen.findByText("스킬 목록을 불러오지 못했습니다"),
    ).toBeInTheDocument();
    // 보여 줄 데이터가 없는 실패라 한 줄 알림은 겹쳐 띄우지 않는다.
    expect(screen.queryByRole("alert")).toBeNull();
    // 목록을 모르는 채로는 다음 단계로 넘어가지 못한다.
    expect(screen.getByRole("button", { name: "다음" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(await screen.findByText("Spring Boot")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다음" })).toBeEnabled();
  });
});
