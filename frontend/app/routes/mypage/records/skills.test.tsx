import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as skillApi from "../../../lib/skillApi";
import { confirmDialog } from "../../../test-utils/confirmDialog";
import RecordsSkills, { clientLoader, ErrorBoundary } from "./skills";

vi.mock("../../../lib/skillApi");

const skills: skillApi.UserSkill[] = [
  {
    id: "us-1",
    skillId: "s-1",
    standardName: "Spring Boot",
    isCustom: false,
    monthsOfExperience: 36,
    lastUsedDate: "2026-08-01",
  },
];

function renderTab() {
  // 스텁 형태는 app/routes/onboarding/income.test.tsx 를 따른다 — HydrateFallback 과
  // /login 라우트가 둘 다 필요한 이유는 Task 5 의 같은 주석을 보라. 이 탭은
  // clientAction 이 없다(이벤트 핸들러로 저장한다).
  const Stub = createRoutesStub([
    {
      path: "/mypage/records/skills",
      Component: RecordsSkills,
      ErrorBoundary,
      loader: clientLoader,
      HydrateFallback: () => null,
    },
    { path: "/login", Component: () => <div>로그인 화면</div> },
  ]);
  return render(<Stub initialEntries={["/mypage/records/skills"]} />);
}

describe("스킬 탭 (S-12)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(skillApi.fetchUserSkills).mockResolvedValue(skills);
    vi.mocked(skillApi.searchSkills).mockResolvedValue([]);
  });

  it("600개월을 넘으면 거부하고 서버를 부르지 않는다", async () => {
    vi.mocked(skillApi.searchSkills).mockResolvedValue([
      { id: "s-2", standardName: "TypeScript", isCustom: false },
    ]);
    renderTab();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("스킬 검색"), "type");
    await user.click(await screen.findByRole("option", { name: "TypeScript" }));
    await user.clear(screen.getByLabelText(/실무 사용 기간/));
    await user.type(screen.getByLabelText(/실무 사용 기간/), "601");
    await user.click(screen.getByRole("button", { name: "스킬 추가" }));

    expect(
      await screen.findByText(
        "실무 사용 기간은 1개월에서 600개월 사이여야 합니다.",
      ),
    ).toBeInTheDocument();
    expect(skillApi.createUserSkill).not.toHaveBeenCalled();
  });

  it("409 는 검색 필드에 인라인으로 붙인다", async () => {
    vi.mocked(skillApi.searchSkills).mockResolvedValue([
      { id: "s-2", standardName: "TypeScript", isCustom: false },
    ]);
    vi.mocked(skillApi.createUserSkill).mockRejectedValue({
      code: "DUPLICATE_SKILL",
      message: "이미 등록된 스킬입니다.",
      status: 409,
    });
    renderTab();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("스킬 검색"), "type");
    await user.click(await screen.findByRole("option", { name: "TypeScript" }));
    await user.type(screen.getByLabelText(/실무 사용 기간/), "12");
    await user.type(screen.getByLabelText(/마지막 사용일/), "2026-08-01");
    await user.click(screen.getByRole("button", { name: "스킬 추가" }));

    // 폼 전체(`_form`)가 아니라 검색 필드(`skillSearch`)에 붙어야 한다 —
    // 단순히 메시지가 어딘가에 떠 있다는 것만으로는 formError 로 잘못
    // 세팅돼도 통과해 버린다. `Field` 가 오류 문단에 다는 id(`${id}-error`,
    // app/components/onboarding/Field.tsx)로 착지 필드를 구분한다.
    const message = await screen.findByText("이미 등록된 스킬입니다.");
    expect(message).toHaveAttribute("id", "skillSearch-error");
  });

  it("삭제하면 해당 id 로 서버를 부르고 목록에서 뺀다", async () => {
    vi.mocked(skillApi.deleteUserSkill).mockResolvedValue(undefined);
    renderTab();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Spring Boot 삭제" }),
    );
    await confirmDialog(user, "삭제");
    await waitFor(() =>
      expect(skillApi.deleteUserSkill).toHaveBeenCalledWith("us-1"),
    );
    await waitFor(() =>
      expect(screen.queryByText("Spring Boot")).not.toBeInTheDocument(),
    );
  });

  it("수정하면 그 id 로 개월 수와 날짜를 보낸다", async () => {
    vi.mocked(skillApi.updateUserSkill).mockResolvedValue({
      ...skills[0],
      monthsOfExperience: 48,
    });
    renderTab();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Spring Boot 수정" }),
    );
    const monthsInput = screen.getByLabelText("Spring Boot 실무 사용 기간");
    await user.clear(monthsInput);
    await user.type(monthsInput, "48");
    await user.click(screen.getByRole("button", { name: "Spring Boot 저장" }));

    await waitFor(() =>
      expect(skillApi.updateUserSkill).toHaveBeenCalledWith("us-1", {
        monthsOfExperience: 48,
        lastUsedDate: "2026-08-01",
      }),
    );
  });

  it("삭제가 실패하면 그 행 옆에 오류를 보여주고 목록은 그대로 둔다", async () => {
    // M4: formError(추가 폼 밑)가 아니라 실패한 행 옆에 붙어야 한다.
    vi.mocked(skillApi.deleteUserSkill).mockRejectedValue({
      code: "UNKNOWN",
      message: "삭제하지 못했습니다.",
      status: 500,
    });
    renderTab();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Spring Boot 삭제" }),
    );
    await confirmDialog(user, "삭제");

    expect(await screen.findByText("삭제하지 못했습니다.")).toBeInTheDocument();
    expect(screen.getByText("Spring Boot")).toBeInTheDocument();
    // 추가 폼의 실무 사용 기간 필드에는 오류가 붙지 않는다.
    expect(document.getElementById("monthsOfExperience-error")).toBeNull();
  });

  it("사전에 없는 검색어면 직접 추가 컨트롤이 나타나고 고르면 분류 선택이 열린다", async () => {
    // I2: searchSkills 가 빈 배열을 주는 경우(사전 미등록)만 이 컨트롤이 떠야 한다.
    vi.mocked(skillApi.searchSkills).mockResolvedValue([]);
    renderTab();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("스킬 검색"), "Zig");
    await waitFor(() =>
      expect(skillApi.searchSkills).toHaveBeenCalledWith("Zig"),
    );

    const addCustomButton = await screen.findByRole("button", {
      name: "+ 'Zig'를 사전에 없는 스킬로 직접 추가하기",
    });
    await user.click(addCustomButton);

    expect(await screen.findByLabelText("스킬 분류")).toBeInTheDocument();
  });

  it("직접 추가한 스킬은 category 를 붙여 생성한다", async () => {
    vi.mocked(skillApi.searchSkills).mockResolvedValue([]);
    vi.mocked(skillApi.createUserSkill).mockResolvedValue({
      id: "us-2",
      skillId: "s-2",
      standardName: "Zig",
      isCustom: true,
      monthsOfExperience: 12,
      lastUsedDate: "2026-08-01",
    });
    renderTab();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("스킬 검색"), "Zig");
    await user.click(
      await screen.findByRole("button", {
        name: "+ 'Zig'를 사전에 없는 스킬로 직접 추가하기",
      }),
    );

    const categorySelect = await screen.findByLabelText("스킬 분류");
    await user.selectOptions(categorySelect, "DEVOPS");
    await user.type(screen.getByLabelText(/실무 사용 기간/), "12");
    await user.type(screen.getByLabelText(/마지막 사용일/), "2026-08-01");
    await user.click(screen.getByRole("button", { name: "스킬 추가" }));

    await waitFor(() =>
      expect(skillApi.createUserSkill).toHaveBeenCalledWith({
        standardName: "Zig",
        monthsOfExperience: 12,
        lastUsedDate: "2026-08-01",
        category: "DEVOPS",
      }),
    );
  });

  it("검색이 실패하면 오류를 보여주고 낡은 결과를 지운다", async () => {
    // M6: 조용히 삼키면 드롭다운이 실패 이전 결과를 최신인 것처럼 계속 보여준다.
    vi.mocked(skillApi.searchSkills)
      .mockResolvedValueOnce([
        { id: "s-2", standardName: "TypeScript", isCustom: false },
      ])
      .mockRejectedValueOnce({
        code: "UNKNOWN",
        message: "검색하지 못했습니다.",
        status: 500,
      });
    renderTab();
    const user = userEvent.setup();

    const input = await screen.findByLabelText("스킬 검색");
    await user.type(input, "type");
    expect(await screen.findByText("TypeScript")).toBeInTheDocument();

    await user.clear(input);
    await user.type(input, "typex");

    expect(await screen.findByText("검색하지 못했습니다.")).toBeInTheDocument();
    expect(screen.queryByText("TypeScript")).not.toBeInTheDocument();
  });
});
