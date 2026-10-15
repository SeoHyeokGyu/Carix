import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import { JobHistoryTable } from "./JobHistoryTable";

const job = {
  jobId: "job-aaaaaaaa-1",
  jobType: "DEDUPLICATION",
  status: "RUNNING",
  startedAt: new Date().toISOString(),
  completedAt: null,
  details: null,
  errorMessage: null,
};

describe("JobHistoryTable", () => {
  it("필터와 상태 배지가 같은 말을 쓴다", () => {
    render(
      <MemoryRouter>
        <JobHistoryTable jobs={[job]} isLoading={false} />
      </MemoryRouter>,
    );
    const filters = screen.getByRole("group", { name: "상태 필터" });
    expect(
      within(filters).getByRole("button", { name: "실행 중" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("실행 중", { selector: "[data-tone]" }),
    ).toBeInTheDocument();
    // 그룹 안에는 필터 버튼만 둔다 — 설명 문장은 그룹 이름을 흐린다.
    expect(within(filters).queryByText(/최근 실행된/)).toBeNull();
  });

  it("유형은 사람이 읽는 이름이다", () => {
    render(
      <MemoryRouter>
        <JobHistoryTable jobs={[job]} isLoading={false} />
      </MemoryRouter>,
    );
    expect(screen.getByText("중복제거")).toHaveAttribute("data-slot", "badge");
  });
});
