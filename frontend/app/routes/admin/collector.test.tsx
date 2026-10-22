import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as collectorApi from "../../lib/collectorApi";
import CollectorAdminPage from "./collector";

vi.mock("../../lib/collectorApi", () => ({
  fetchPipelineStats: vi.fn(),
  fetchRecentJobs: vi.fn(),
  getAdminKey: vi.fn(),
  setAdminKey: vi.fn(),
  clearAdminKey: vi.fn(),
  triggerCrawl: vi.fn(),
  triggerWageStat: vi.fn(),
  triggerDeduplication: vi.fn(),
  triggerMarketAggregate: vi.fn(),
  triggerSeedLoad: vi.fn(),
}));

describe("CollectorAdminPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(collectorApi.fetchPipelineStats).mockResolvedValue({
      totalRawIngest: 500,
      totalJobPostings: 450,
      totalSkills: 120,
      totalWageStats: 24,
      postingsBySource: { JOBKOREA: 250, SARAMIN: 200 },
    });

    vi.mocked(collectorApi.fetchRecentJobs).mockResolvedValue([
      {
        jobId: "job-12345678-abcd",
        jobType: "CRAWL",
        status: "SUCCESS",
        startedAt: new Date().toISOString(),
        completedAt: new Date().toISOString(),
        details: { ingestedJobPostings: 15, portal: "SARAMIN" },
        errorMessage: null,
      },
    ]);

    vi.mocked(collectorApi.getAdminKey).mockReturnValue("sample-admin-key");
  });

  it("통계를 못 불러오면 그 구획에 다시 시도를 두고 누르면 다시 부른다", async () => {
    vi.mocked(collectorApi.fetchPipelineStats).mockRejectedValueOnce(
      new Error("boom"),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );

    expect(
      await screen.findByText("수집 현황을 불러오지 못했습니다"),
    ).toBeInTheDocument();
    // 이력은 불러왔으니 화면 전체가 아니라 그 구획만 실패다.
    expect(screen.getByText("job-1234...")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(await screen.findByText("500")).toBeInTheDocument();
    expect(collectorApi.fetchPipelineStats).toHaveBeenCalledTimes(2);
  });

  it("원티드를 고르면 페이지·간격 입력을 숨기고 안내를 보인다", async () => {
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("500")).toBeInTheDocument();
    });
    expect(
      screen.getByRole("option", { name: "원티드 (Wanted)" }),
    ).toBeInTheDocument();

    fireEvent.change(screen.getByDisplayValue(/전체 포털/), {
      target: { value: "WANTED" },
    });

    expect(screen.queryByText(/수집 페이지 수/)).not.toBeInTheDocument();
    expect(screen.queryByText(/요청 지연 간격/)).not.toBeInTheDocument();
    expect(
      screen.getByText(
        "원티드는 개발 공고 목록 전체를 순회합니다(첫 실행 약 1시간 30분, 이후 신규만). 그동안 다른 작업은 대기",
      ),
    ).toBeInTheDocument();
  });

  it("관리자 키 모달 열기 및 저장이 동작한다", async () => {
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("500")).toBeInTheDocument();
    });

    // 키 상태는 상태 줄의 배지, 키 창은 수집 데이터 화면처럼 헤더 「관리자 키」로 연다.
    expect(screen.getByText("키 등록됨")).toHaveAttribute(
      "data-tone",
      "positive",
    );
    const keyBtn = screen.getByRole("button", { name: "관리자 키" });
    fireEvent.click(keyBtn);

    // 대화상자 제목은 확인창과 같은 h2 이고 이모지를 붙이지 않는다.
    expect(
      screen.getByRole("heading", {
        level: 2,
        name: "Collector 관리자 시크릿 키 설정",
      }),
    ).toBeInTheDocument();

    const saveBtn = screen.getByRole("button", { name: "저장하기" });
    expect(saveBtn).toHaveAttribute("data-slot", "button");
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(collectorApi.setAdminKey).toHaveBeenCalled();
    });
    // 새로고침 버튼 대신 키를 바꾸면 그 키로 다시 불러온다.
    await waitFor(() => {
      expect(collectorApi.fetchPipelineStats).toHaveBeenCalledTimes(2);
      expect(collectorApi.fetchRecentJobs).toHaveBeenCalledTimes(2);
    });
  });

  it("받아 둔 데이터가 있는 채 다시 불러오기에 실패하면 내용을 남기고 한 줄로 알린다", async () => {
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText("500")).toBeInTheDocument();
    vi.mocked(collectorApi.fetchPipelineStats).mockRejectedValueOnce(
      new Error("401"),
    );
    vi.mocked(collectorApi.fetchRecentJobs).mockRejectedValueOnce(
      new Error("401"),
    );

    // 저장된 키를 지우면 다시 불러오고, 그 실패가 옛 데이터 위에 보인다.
    fireEvent.click(screen.getByRole("button", { name: "관리자 키" }));
    const clear = screen.getByRole("button", { name: "저장된 키 삭제" });
    // 키 삭제는 되돌릴 수 있는 계정 동작이라 보조(outline)다.
    expect(clear).toHaveAttribute("data-variant", "outline");
    fireEvent.click(clear);

    expect(
      await screen.findByText("수집 현황을 다시 불러오지 못했습니다"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("작업 실행 이력을 다시 불러오지 못했습니다"),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("alert").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("500")).toBeInTheDocument();
    expect(screen.getByText("job-1234...")).toBeInTheDocument();
  });

  it("먼저 보낸 요청이 늦게 와도 나중 요청의 결과를 덮지 않는다", async () => {
    let resolveFirst: (
      v: Awaited<ReturnType<typeof collectorApi.fetchPipelineStats>>,
    ) => void = () => {};
    vi.mocked(collectorApi.fetchPipelineStats)
      .mockReturnValueOnce(
        new Promise((r) => {
          resolveFirst = r;
        }),
      )
      .mockResolvedValueOnce({
        totalRawIngest: 777,
        totalJobPostings: 1,
        totalSkills: 1,
        totalWageStats: 1,
        postingsBySource: {},
      });
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );
    document.dispatchEvent(new Event("visibilitychange"));
    expect(await screen.findByText("777")).toBeInTheDocument();

    resolveFirst({
      totalRawIngest: 111,
      totalJobPostings: 1,
      totalSkills: 1,
      totalWageStats: 1,
      postingsBySource: {},
    });
    await new Promise((r) => setTimeout(r, 0));
    expect(screen.queryByText("111")).toBeNull();
    expect(screen.getByText("777")).toBeInTheDocument();
  });
});
