import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import userEvent from "@testing-library/user-event";
import CollectorAdminPage from "./collector";
import { expectHeadingOutline } from "../../test-utils/headingOutline";
import * as collectorApi from "../../lib/collectorApi";

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

  it("헤더에 데이터 상태 링크가 없다", async () => {
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );
    expect(
      screen.queryByRole("link", { name: "데이터 상태" }),
    ).not.toBeInTheDocument();
  });

  it("콘솔 타이틀과 요약 카드를 정상 렌더링한다", async () => {
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );

    // 화면 틀은 PageFrame admin + PageHeader, 제목은 관리자 탭 이름이다.
    expect(
      screen.getByRole("heading", { level: 1, name: "수집 파이프라인" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("main")).toHaveAttribute(
      "data-page-frame",
      "admin",
    );

    await waitFor(() => {
      expect(screen.getByText("500")).toBeInTheDocument();
      expect(screen.getByText("450")).toBeInTheDocument();
      expect(screen.getByText("120")).toBeInTheDocument();
      expect(screen.getByText("24")).toBeInTheDocument();
    });
    for (const name of ["수집 현황", "파이프라인 실행", "작업 실행 이력"]) {
      expect(
        screen.getByRole("heading", { level: 2, name }),
      ).toBeInTheDocument();
    }
    expectHeadingOutline();
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

  it("이력을 못 불러오면 이력 구획에 실패를 보인다", async () => {
    vi.mocked(collectorApi.fetchRecentJobs).mockRejectedValueOnce(
      new Error("boom"),
    );
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );
    expect(
      await screen.findByText("작업 실행 이력을 불러오지 못했습니다"),
    ).toBeInTheDocument();
    expect(screen.queryByText("실행 이력이 없습니다.")).not.toBeInTheDocument();
  });

  it("불러오는 동안은 LoadingState 다", () => {
    vi.mocked(collectorApi.fetchPipelineStats).mockReturnValue(
      new Promise(() => {}),
    );
    vi.mocked(collectorApi.fetchRecentJobs).mockReturnValue(
      new Promise(() => {}),
    );
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );
    expect(screen.getAllByRole("status").length).toBeGreaterThanOrEqual(2);
    expect(document.querySelector(".animate-pulse")).toBeNull();
  });

  it("작업 실행 이력을 표시하고 최근 수집 공고 미리보기는 없다", async () => {
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("job-1234...")).toBeInTheDocument();
    });
    expect(screen.getByRole("table")).toHaveAttribute("data-slot", "table");
    expect(
      screen.getByText("성공", { selector: "[data-tone]" }),
    ).toHaveAttribute("data-tone", "positive");
    expect(
      screen.queryByText("최근 수집된 공고 및 정규화 결과 미리보기"),
    ).not.toBeInTheDocument();
  });

  it("성공한 크롤링 작업에 수집 데이터 링크를 건다", async () => {
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );

    const link = await screen.findByRole("link", {
      name: "이 달 신규 공고 보기",
    });
    // 표 안 링크는 늘 밑줄인 TextLink(none) 다.
    expect(link).toHaveClass("underline", "font-medium");
    const href = link.getAttribute("href") ?? "";
    expect(href).toMatch(
      /^\/admin\/data\?tab=postings&month=\d{6}&sort=recent&source=SARAMIN$/,
    );
  });

  it("제어 패널에서 탭을 전환할 수 있다", async () => {
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("500")).toBeInTheDocument();
    });

    const wageTabButton = screen.getByRole("tab", {
      name: /KOSIS 공식 임금 수집/i,
    });
    fireEvent.click(wageTabButton);
    expect(wageTabButton).toHaveAttribute("aria-selected", "true");

    await waitFor(() => {
      expect(
        screen.getByText("KOSIS 국가 공식 임금 통계 수집 및 보간"),
      ).toBeInTheDocument();
    });
  });

  it("점핏을 고르면 무시되는 페이지·간격 입력을 숨기고 안내를 보인다", async () => {
    render(
      <MemoryRouter>
        <CollectorAdminPage />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("500")).toBeInTheDocument();
    });
    expect(screen.getByText(/수집 페이지 수/)).toBeInTheDocument();
    expect(screen.getByText(/요청 지연 간격/)).toBeInTheDocument();

    fireEvent.change(screen.getByDisplayValue(/전체 포털/), {
      target: { value: "JUMPIT" },
    });

    expect(screen.queryByText(/수집 페이지 수/)).not.toBeInTheDocument();
    expect(screen.queryByText(/요청 지연 간격/)).not.toBeInTheDocument();
    expect(
      screen.getByText(/점핏은 목록 전체를 순회합니다/),
    ).toBeInTheDocument();
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

    const keyBtn = screen.getByRole("button", { name: /키 등록됨/i });
    fireEvent.click(keyBtn);

    expect(
      screen.getByText("Collector 관리자 시크릿 키 설정"),
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
    fireEvent.click(screen.getByRole("button", { name: /키 등록됨/i }));
    fireEvent.click(screen.getByRole("button", { name: "저장된 키 삭제" }));

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
});
