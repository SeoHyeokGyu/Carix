import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub, Link, useNavigate } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DataExplorePage from "./data";
import * as api from "../../lib/collectorApi";

vi.mock("../../lib/collectorApi", async (orig) => ({
  ...(await orig<typeof import("../../lib/collectorApi")>()),
  fetchMonthlyOverview: vi.fn(),
  fetchPostings: vi.fn(),
  fetchCpi: vi.fn(),
  fetchDataHealth: vi.fn(),
}));

const monthly: api.MonthlyOverview = {
  from: "202608",
  to: "202609",
  months: [
    {
      month: "202608",
      jobkoreaSampled: 250,
      jobkoreaSampledCapped: 244,
      jobkoreaCrawled: 1804,
      saraminCrawled: 400,
      sampledByFamily: { DEVELOPER: 200, DATA: 9, AI: 35 },
      wantedSampled: 60,
      wantedSampledCapped: 58,
      wantedCrawled: 310,
      current: false,
    },
    {
      month: "202609",
      jobkoreaSampled: 150,
      jobkoreaSampledCapped: 148,
      jobkoreaCrawled: 1248,
      saraminCrawled: 410,
      sampledByFamily: { DEVELOPER: 120, DATA: 10, AI: 18 },
      wantedSampled: 70,
      wantedSampledCapped: 68,
      wantedCrawled: 320,
      current: false,
    },
  ],
};

const healthyReport: api.DataHealthReport = {
  evaluatedAt: "2026-10-08T00:25:00Z",
  referenceMonth: "202610",
  previousMonth: "202609",
  engineRuleVersion: "1.3.0",
  summary: { users: 3, fullyAnswered: 3, gaps: [] },
  checks: [
    "AGGREGATE_STALE",
    "RULE_VERSION",
    "SNAPSHOT_FRESHNESS",
    "CPI_LATEST",
    "CRAWL_FRESHNESS",
  ].map((id) => ({
    id,
    title: id,
    verdict: "OK" as const,
    message: "정상",
    impactUsers: null,
    action: null,
    actionLink: null,
    evidence: {},
    drillDown: null,
  })),
};

const postings: api.PostingPage = {
  total: 1,
  page: 0,
  size: 50,
  items: [
    {
      source: "JOBKOREA",
      externalId: "49500742",
      url: "https://www.jobkorea.co.kr/Recruit/GI_Read/49500742",
      title: "QA 채용",
      companyName: "(주)평화이즈",
      jobFamily: "DEVELOPER",
      experienceMin: 3,
      experienceMax: null,
      employmentType: "FULL_TIME",
      region: "서울 서초구",
      postedStartDate: "2026-07-02",
      postedEndDate: "2026-07-12",
      firstSeenMonth: "202607",
      sampledMonth: "202607",
      salaryMin: 50_000_000,
      salaryMax: null,
      salaryBandMin: null,
      salaryBandMax: null,
      salaryDisclosure: "PUBLIC",
      skills: ["QA"],
      collectedVia: null,
      sourceJobTags: null,
      collectedAt: "2026-07-03T01:00:00Z",
    },
  ],
};

function PageWithBackLink() {
  const navigate = useNavigate();
  return (
    <>
      <button type="button" onClick={() => navigate(-1)}>
        뒤로
      </button>
      <Link to="/admin/data?tab=postings&month=202607">공고로 돌아가기</Link>
      <DataExplorePage />
    </>
  );
}

function renderAt(url: string | string[]) {
  const Stub = createRoutesStub([
    { path: "/admin/data", Component: PageWithBackLink },
  ]);
  render(<Stub initialEntries={Array.isArray(url) ? url : [url]} />);
}

describe("DataExplorePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.fetchMonthlyOverview).mockResolvedValue(monthly);
    vi.mocked(api.fetchPostings).mockResolvedValue(postings);
    vi.mocked(api.fetchDataHealth).mockResolvedValue(healthyReport);
  });

  it("기본 탭은 월별 현황이고 얇은 칸을 강조한다", async () => {
    renderAt("/admin/data");
    await screen.findByText("150");
    const thin = screen.getByText("148");
    expect(thin).toHaveAttribute("data-thin", "true");
    expect(screen.getByText("9")).toHaveAttribute("data-thin", "true");
    expect(screen.getByText("10")).toHaveAttribute("data-thin", "false");
  });

  it("URL 의 tab·month·sampled 로 공고 탭을 연다", async () => {
    renderAt("/admin/data?tab=postings&month=202607&sampled=true");
    expect(await screen.findByText("QA 채용")).toBeInTheDocument();
    expect(api.fetchPostings).toHaveBeenCalledWith(
      expect.objectContaining({ month: "202607", sampled: true, page: 0 }),
    );
  });

  it("URL 의 from·to 를 월별·CPI 조회에 그대로 넘긴다", async () => {
    renderAt("/admin/data?tab=monthly&from=202310&to=202609");
    await screen.findByText("150");
    expect(api.fetchMonthlyOverview).toHaveBeenCalledWith(
      "202310",
      "202609",
      undefined,
    );
  });

  it("월별 탭의 시작·끝 월을 바꾸면 URL 과 조회가 따라간다", async () => {
    renderAt("/admin/data?tab=monthly&from=202310&to=202609");
    await screen.findByText("150");
    expect(screen.getByLabelText("시작 월")).toHaveValue("2023-10");
    expect(screen.getByLabelText("끝 월")).toHaveValue("2026-09");
    fireEvent.change(screen.getByLabelText("시작 월"), {
      target: { value: "2024-01" },
    });
    await waitFor(() =>
      expect(api.fetchMonthlyOverview).toHaveBeenLastCalledWith(
        "202401",
        "202609",
        undefined,
      ),
    );
    fireEvent.change(screen.getByLabelText("끝 월"), {
      target: { value: "2026-08" },
    });
    await waitFor(() =>
      expect(api.fetchMonthlyOverview).toHaveBeenLastCalledWith(
        "202401",
        "202608",
        undefined,
      ),
    );
  });

  it("기간 URL 이 없을 때 한쪽만 바꿔도 보이던 다른 쪽을 함께 남긴다", async () => {
    renderAt("/admin/data");
    await screen.findByText("150");
    fireEvent.change(screen.getByLabelText("끝 월"), {
      target: { value: "2026-08" },
    });
    await waitFor(() =>
      expect(api.fetchMonthlyOverview).toHaveBeenLastCalledWith(
        "202608",
        "202608",
        undefined,
      ),
    );
  });

  it("시작 월이 끝 월보다 늦으면 메시지를 보이고 URL·조회를 바꾸지 않는다", async () => {
    renderAt("/admin/data?tab=monthly&from=202310&to=202609");
    await screen.findByText("150");
    const calls = vi.mocked(api.fetchMonthlyOverview).mock.calls.length;
    fireEvent.change(screen.getByLabelText("시작 월"), {
      target: { value: "2026-10" },
    });
    expect(
      await screen.findByText("시작 월이 끝 월보다 늦습니다"),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("시작 월")).toHaveValue("2023-10");
    expect(api.fetchMonthlyOverview).toHaveBeenCalledTimes(calls);
    fireEvent.change(screen.getByLabelText("끝 월"), {
      target: { value: "2023-09" },
    });
    expect(
      await screen.findByText("시작 월이 끝 월보다 늦습니다"),
    ).toBeInTheDocument();
    expect(api.fetchMonthlyOverview).toHaveBeenCalledTimes(calls);
  });

  it("기간이 36개월을 넘으면 메시지를 보이고, 다음 올바른 입력에서 메시지가 사라진다", async () => {
    renderAt("/admin/data?tab=monthly&from=202310&to=202609");
    await screen.findByText("150");
    const calls = vi.mocked(api.fetchMonthlyOverview).mock.calls.length;
    fireEvent.change(screen.getByLabelText("시작 월"), {
      target: { value: "2023-09" },
    });
    expect(
      await screen.findByText("기간은 최대 36개월입니다"),
    ).toBeInTheDocument();
    expect(api.fetchMonthlyOverview).toHaveBeenCalledTimes(calls);
    fireEvent.change(screen.getByLabelText("시작 월"), {
      target: { value: "2023-11" },
    });
    await waitFor(() =>
      expect(
        screen.queryByText("기간은 최대 36개월입니다"),
      ).not.toBeInTheDocument(),
    );
  });

  it("sampleSource 는 소문자여도 정규화해 선택 상자와 머리글에 반영한다", async () => {
    renderAt("/admin/data?tab=monthly&sampleSource=wanted");
    await screen.findByText("150");
    expect(screen.getByLabelText("직무별 표본 원천")).toHaveValue("WANTED");
    expect(
      screen.getByRole("columnheader", { name: "직무별 표본 (원티드)" }),
    ).toBeInTheDocument();
  });

  it("월별 현황에 원티드 표본·상한 적용·정기 열을 보인다", async () => {
    renderAt("/admin/data");
    await screen.findByText("150");
    expect(
      screen.getByRole("columnheader", { name: "원티드" }),
    ).toBeInTheDocument();
    expect(screen.getByText("60")).toBeInTheDocument();
    expect(screen.getByText("68")).toBeInTheDocument();
    expect(screen.getByText("320")).toBeInTheDocument();
  });

  it("URL 의 sampleSource 를 월별 조회에 넘기고 선택 상자로 바꾼다", async () => {
    renderAt("/admin/data?tab=monthly&sampleSource=WANTED");
    await screen.findByText("150");
    expect(api.fetchMonthlyOverview).toHaveBeenCalledWith(
      undefined,
      undefined,
      "WANTED",
    );
    const select = screen.getByLabelText("직무별 표본 원천");
    expect(select).toHaveValue("WANTED");
    await userEvent.selectOptions(select, "JOBKOREA");
    await waitFor(() =>
      expect(api.fetchMonthlyOverview).toHaveBeenLastCalledWith(
        undefined,
        undefined,
        "JOBKOREA",
      ),
    );
  });

  it("공고 탭 원천 선택에 원티드가 있다", async () => {
    renderAt("/admin/data?tab=postings&month=202607&source=WANTED");
    await screen.findByText("QA 채용");
    expect(screen.getByLabelText("원천")).toHaveValue("WANTED");
    expect(api.fetchPostings).toHaveBeenCalledWith(
      expect.objectContaining({ source: "WANTED" }),
    );
  });

  it("CPI 탭도 URL 의 from·to 를 넘긴다", async () => {
    vi.mocked(api.fetchCpi).mockResolvedValue({
      codes: ["A"],
      months: [{ month: "202609", values: { A: 118.3 } }],
      missing: [],
    });
    renderAt("/admin/data?tab=cpi&from=201610&to=202609");
    await screen.findByText("118.30");
    expect(api.fetchCpi).toHaveBeenCalledWith("201610", "202609");
  });

  it("공개 구분 필터를 URL 로 넘긴다", async () => {
    renderAt("/admin/data?tab=postings&month=202607&disclosure=BAND");
    await screen.findByText("QA 채용");
    expect(api.fetchPostings).toHaveBeenCalledWith(
      expect.objectContaining({ disclosure: "BAND" }),
    );
  });

  it("정렬 기본값은 최근 수집순이고 URL 의 sort 를 API 로 넘긴다", async () => {
    renderAt("/admin/data?tab=postings&month=202607");
    await screen.findByText("QA 채용");
    expect(screen.getByLabelText("정렬")).toHaveValue("recent");
    expect(api.fetchPostings).toHaveBeenCalledWith(
      expect.objectContaining({ sort: "recent" }),
    );
  });

  it("URL 의 sort=source 를 선택 상태와 API 에 반영한다", async () => {
    renderAt("/admin/data?tab=postings&month=202607&sort=source");
    await screen.findByText("QA 채용");
    expect(screen.getByLabelText("정렬")).toHaveValue("source");
    expect(api.fetchPostings).toHaveBeenCalledWith(
      expect.objectContaining({ sort: "source" }),
    );
  });

  it("정렬을 바꿔 조회하면 다시 불러온다", async () => {
    renderAt("/admin/data?tab=postings&month=202607");
    await screen.findByText("QA 채용");
    await userEvent.selectOptions(screen.getByLabelText("정렬"), "source");
    await userEvent.click(screen.getByRole("button", { name: "조회" }));
    await waitFor(() =>
      expect(api.fetchPostings).toHaveBeenLastCalledWith(
        expect.objectContaining({ sort: "source", page: 0 }),
      ),
    );
  });

  it("공고 탭에 month 가 없으면 UTC 지난달을 채워 조회한다", async () => {
    vi.useFakeTimers({
      toFake: ["Date"],
      now: new Date("2026-10-31T16:00:00Z"),
    });
    try {
      renderAt("/admin/data?tab=postings");
      expect(await screen.findByText("QA 채용")).toBeInTheDocument();
      expect(api.fetchPostings).toHaveBeenCalledWith(
        expect.objectContaining({ month: "202609" }),
      );
      expect(screen.getByLabelText("월")).toHaveValue("2026-09");
    } finally {
      vi.useRealTimers();
    }
  });

  it("기본 월을 채워도 브라우저 기록을 쌓지 않는다", async () => {
    renderAt(["/admin/data?tab=monthly", "/admin/data?tab=postings"]);
    await screen.findByText("QA 채용");
    await userEvent.click(screen.getByRole("button", { name: "뒤로" }));
    expect(await screen.findByText("150")).toBeInTheDocument();
  });

  it("URL 에 month 가 있으면 덮어쓰지 않는다", async () => {
    renderAt("/admin/data?tab=postings&month=202605&source=WANTED");
    await screen.findByText("QA 채용");
    expect(api.fetchPostings).toHaveBeenCalledTimes(1);
    expect(api.fetchPostings).toHaveBeenCalledWith(
      expect.objectContaining({ month: "202605", source: "WANTED" }),
    );
    const input = screen.getByLabelText("월");
    expect(input).toHaveAttribute("type", "month");
    expect(input).toHaveValue("2026-05");
    expect(input).toBeRequired();
  });

  it("월별 탭에서 공고 탭을 누르면 UTC 지난달로 한 번 조회한다", async () => {
    vi.useFakeTimers({
      toFake: ["Date"],
      now: new Date("2026-10-31T16:00:00Z"),
    });
    try {
      renderAt("/admin/data?tab=monthly");
      await screen.findByText("150");
      await userEvent.click(screen.getByRole("tab", { name: "공고" }));
      expect(await screen.findByText("QA 채용")).toBeInTheDocument();
      expect(screen.getByLabelText("월")).toHaveValue("2026-09");
      expect(api.fetchPostings).toHaveBeenCalledTimes(1);
      expect(api.fetchPostings).toHaveBeenCalledWith(
        expect.objectContaining({ month: "202609" }),
      );
    } finally {
      vi.useRealTimers();
    }
  });

  it("월 입력을 바꿔 조회하면 yyyyMM 으로 조회한다", async () => {
    renderAt("/admin/data?tab=postings&month=202605");
    await screen.findByText("QA 채용");
    fireEvent.change(screen.getByLabelText("월"), {
      target: { value: "2026-03" },
    });
    await userEvent.click(screen.getByRole("button", { name: "조회" }));
    await waitFor(() =>
      expect(api.fetchPostings).toHaveBeenLastCalledWith(
        expect.objectContaining({ month: "202603" }),
      ),
    );
  });

  it("필터 선택 항목이 표시명을 쓴다", async () => {
    renderAt("/admin/data?tab=postings&month=202605");
    await screen.findByText("QA 채용");
    const optionTexts = (label: string) =>
      within(screen.getByLabelText(label))
        .getAllByRole("option")
        .map((o) => o.textContent);
    expect(optionTexts("원천")).toEqual([
      "전체 원천",
      "잡코리아",
      "사람인",
      "원티드",
    ]);
    expect(optionTexts("직무")).toEqual([
      "전체 직무",
      "개발",
      "데이터",
      "AI",
      "기타",
    ]);
    expect(optionTexts("표본 여부")[0]).toBe("표본·정기 모두");
  });

  it("월 형식이 틀리면 조회 없이 안내한다", async () => {
    renderAt("/admin/data?tab=postings&month=2026-09");
    expect(
      await screen.findByText("월은 YYYYMM 형식입니다"),
    ).toBeInTheDocument();
    expect(api.fetchPostings).not.toHaveBeenCalled();
    expect(screen.queryByText("불러오지 못했습니다")).not.toBeInTheDocument();
  });

  it("공고 행을 펼치면 원천 링크가 새 창·noopener 로 열린다", async () => {
    renderAt("/admin/data?tab=postings&month=202607");
    await userEvent.click(
      await screen.findByRole("button", { name: "QA 채용 상세" }),
    );
    expect(screen.getByText("5,000만원~")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "원천 공고" });
    expect(link).toHaveAttribute("href", postings.items[0].url);
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("postings tab shows empty message", async () => {
    vi.mocked(api.fetchPostings).mockResolvedValue({
      total: 0,
      page: 0,
      size: 50,
      items: [],
    });
    renderAt("/admin/data?tab=postings&month=202001");
    expect(
      await screen.findByText("해당 조건의 데이터가 없습니다"),
    ).toBeInTheDocument();
  });

  it("불러오지 못하면 재시도를 보인다", async () => {
    vi.mocked(api.fetchMonthlyOverview).mockRejectedValue({
      code: "X",
      message: "x",
      status: 500,
    });
    renderAt("/admin/data");
    expect(await screen.findByText("불러오지 못했습니다")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });

  it("월별 현황의 월을 누르면 그 달 공고 탭으로 간다", async () => {
    renderAt("/admin/data");
    await userEvent.click(await screen.findByRole("link", { name: "202608" }));
    expect(api.fetchPostings).toHaveBeenCalledWith(
      expect.objectContaining({ month: "202608" }),
    );
    expect(
      within(screen.getByRole("tablist")).getByRole("tab", { name: "공고" }),
    ).toHaveAttribute("aria-selected", "true");
  });

  it("늦게 도착한 월별 응답이 공고 탭을 덮어쓰지 않는다", async () => {
    let resolveMonthly!: (v: api.MonthlyOverview) => void;
    vi.mocked(api.fetchMonthlyOverview).mockReturnValue(
      new Promise((r) => {
        resolveMonthly = r;
      }),
    );
    renderAt("/admin/data?tab=postings&month=202607");
    expect(await screen.findByText("QA 채용")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: "월별 현황" }));
    await userEvent.click(
      screen.getByRole("link", { name: "공고로 돌아가기" }),
    );
    expect(await screen.findByText("QA 채용")).toBeInTheDocument();
    await act(async () => {
      resolveMonthly(monthly);
    });
    expect(screen.getByText("QA 채용")).toBeInTheDocument();
  });

  it("필터 제출이 URL 파라미터로 fetchPostings 를 부른다", async () => {
    renderAt("/admin/data?tab=postings&month=202607");
    await screen.findByText("QA 채용");
    await userEvent.type(screen.getByLabelText("회사"), "평화");
    await userEvent.click(screen.getByRole("button", { name: "조회" }));
    await waitFor(() =>
      expect(api.fetchPostings).toHaveBeenLastCalledWith(
        expect.objectContaining({ month: "202607", company: "평화", page: 0 }),
      ),
    );
  });

  it("월별 결과가 비어 있으면 빈 메시지를 보인다", async () => {
    vi.mocked(api.fetchMonthlyOverview).mockResolvedValue({
      from: "202608",
      to: "202609",
      months: [],
    });
    renderAt("/admin/data");
    expect(
      await screen.findByText("해당 조건의 데이터가 없습니다"),
    ).toBeInTheDocument();
  });

  it("403 이면 관리자 키 모달을 열고 실패 알림은 띄우지 않는다", async () => {
    vi.mocked(api.fetchMonthlyOverview).mockRejectedValue({
      code: "FORBIDDEN",
      message: "x",
      status: 403,
    });
    renderAt("/admin/data");
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(screen.queryByText("불러오지 못했습니다")).not.toBeInTheDocument();
  });

  it("CPI 탭 403 이면 키 모달을 열고, 키 저장 뒤 다시 불러온다", async () => {
    vi.mocked(api.fetchCpi)
      .mockRejectedValueOnce({ code: "FORBIDDEN", message: "x", status: 403 })
      .mockResolvedValueOnce({
        codes: ["A"],
        months: [{ month: "202609", values: { A: 118.32 } }],
        missing: [],
      });
    renderAt("/admin/data?tab=cpi");
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(screen.queryByText("불러오지 못했습니다")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "저장하기" }));
    expect(await screen.findByText("118.32")).toBeInTheDocument();
    expect(api.fetchCpi).toHaveBeenCalledTimes(2);
  });

  it("점검 응답이 실패해도 탭 내용은 그려진다", async () => {
    vi.mocked(api.fetchDataHealth).mockRejectedValue({ status: 500 });
    renderAt("/admin/data");
    expect(await screen.findByText("150")).toBeInTheDocument();
    expect(
      await screen.findByText("점검을 불러오지 못했습니다"),
    ).toBeInTheDocument();
  });

  it("탭을 바꾸거나 다시 렌더해도 점검은 처음 한 번만 부른다", async () => {
    renderAt("/admin/data");
    await screen.findByText("점검 정상");
    await userEvent.click(screen.getByRole("tab", { name: "공고" }));
    await userEvent.click(screen.getByRole("tab", { name: "월별 현황" }));
    await screen.findByText("150");
    expect(api.fetchDataHealth).toHaveBeenCalledTimes(1);
    await userEvent.click(screen.getByRole("button", { name: "다시 점검" }));
    await waitFor(() => expect(api.fetchDataHealth).toHaveBeenCalledTimes(2));
  });

  it("키를 저장하면 점검도 다시 부른다", async () => {
    vi.mocked(api.fetchDataHealth).mockRejectedValueOnce({ status: 403 });
    renderAt("/admin/data");
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "저장하기" }));
    expect(await screen.findByText("점검 정상")).toBeInTheDocument();
    expect(api.fetchDataHealth).toHaveBeenCalledTimes(2);
  });

  it("근거 링크는 같은 화면에서 탭만 바꾸고 점검은 다시 부르지 않는다", async () => {
    const withLink: api.DataHealthReport = {
      ...healthyReport,
      checks: healthyReport.checks.map((c) =>
        c.id === "CPI_LATEST"
          ? {
              ...c,
              verdict: "WARN" as const,
              drillDown: "/admin/data?tab=cpi",
            }
          : c,
      ),
    };
    vi.mocked(api.fetchDataHealth).mockResolvedValue(withLink);
    vi.mocked(api.fetchCpi).mockResolvedValue({
      codes: ["A"],
      months: [],
      missing: [],
    });
    renderAt("/admin/data?tab=monthly");
    await userEvent.click(
      await screen.findByRole("link", { name: "CPI_LATEST 근거" }),
    );
    expect(await screen.findByRole("tab", { name: "CPI" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(api.fetchDataHealth).toHaveBeenCalledTimes(1);
  });

  function withDrillDown(id: string, drillDown: string): api.DataHealthReport {
    return {
      ...healthyReport,
      checks: healthyReport.checks.map((c) =>
        c.id === id ? { ...c, verdict: "WARN" as const, drillDown } : c,
      ),
    };
  }

  it("재집계 누락의 근거 링크를 누르면 그 기간으로 월별 현황을 부른다", async () => {
    vi.mocked(api.fetchDataHealth).mockResolvedValue(
      withDrillDown(
        "AGGREGATE_STALE",
        "/admin/data?tab=monthly&from=202311&to=202610",
      ),
    );
    vi.mocked(api.fetchCpi).mockResolvedValue({
      codes: ["A"],
      months: [],
      missing: [],
    });
    renderAt("/admin/data?tab=cpi");
    await userEvent.click(
      await screen.findByRole("link", { name: "AGGREGATE_STALE 근거" }),
    );
    await waitFor(() =>
      expect(api.fetchMonthlyOverview).toHaveBeenLastCalledWith(
        "202311",
        "202610",
        undefined,
      ),
    );
    expect(screen.getByLabelText("시작 월")).toHaveValue("2023-11");
  });

  it("정기 수집 최신성의 근거 링크를 누르면 그 월·원천·정렬로 공고를 부른다", async () => {
    vi.mocked(api.fetchDataHealth).mockResolvedValue(
      withDrillDown(
        "CRAWL_FRESHNESS",
        "/admin/data?tab=postings&month=202609&sort=recent&source=WANTED",
      ),
    );
    renderAt("/admin/data");
    await userEvent.click(
      await screen.findByRole("link", { name: "CRAWL_FRESHNESS 근거" }),
    );
    await waitFor(() =>
      expect(api.fetchPostings).toHaveBeenLastCalledWith(
        expect.objectContaining({
          month: "202609",
          source: "WANTED",
          sort: "recent",
        }),
      ),
    );
  });
});
