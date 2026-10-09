import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import CollectorAdminPage from "./collector";
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

    expect(screen.getByText("Collector 제어 콘솔")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("500")).toBeInTheDocument();
      expect(screen.getByText("450")).toBeInTheDocument();
      expect(screen.getByText("120")).toBeInTheDocument();
      expect(screen.getByText("24")).toBeInTheDocument();
    });
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

    const wageTabButton = screen.getByRole("button", {
      name: /KOSIS 공식 임금 수집/i,
    });
    fireEvent.click(wageTabButton);

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
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(collectorApi.setAdminKey).toHaveBeenCalled();
    });
  });
});
