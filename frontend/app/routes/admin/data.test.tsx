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

import * as api from "../../lib/collectorApi";
import DataExplorePage from "./data";

vi.mock("../../lib/collectorApi", async (orig) => ({
  ...(await orig<typeof import("../../lib/collectorApi")>()),
  fetchMonthlyOverview: vi.fn(),
  fetchPostings: vi.fn(),
  fetchCpi: vi.fn(),
  fetchWage: vi.fn(),
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
  month: "202607",
  current: false,
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

const LOADING = "불러오는 중입니다...";

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
    // 관리자 헤더(max-w-7xl)와 같은 폭이라 제목이 로고 선에서 시작한다.
    expect(screen.getByRole("main")).toHaveClass("max-w-7xl");
    expect(
      screen.getByRole("heading", { level: 1, name: "수집 데이터" }),
    ).toHaveClass("font-display");
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

  it("공고 탭에 month 가 없으면 month 없이 한 번만 조회하고 서버가 정한 월을 URL 에 채운다", async () => {
    vi.mocked(api.fetchPostings).mockResolvedValue({
      ...postings,
      month: "202610",
    });
    renderAt("/admin/data?tab=postings");
    expect(await screen.findByText("QA 채용")).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByLabelText("월")).toHaveValue("2026-10"),
    );
    expect(api.fetchPostings).toHaveBeenCalledTimes(1);
    expect(vi.mocked(api.fetchPostings).mock.calls[0][0].month).toBeUndefined();
    expect(screen.getByText("QA 채용")).toBeInTheDocument();
  });

  it("월 형식이 틀리면 조회 없이 안내한다", async () => {
    renderAt("/admin/data?tab=postings&month=2026-09");
    expect(
      await screen.findByText("월은 YYYYMM 형식입니다"),
    ).toBeInTheDocument();
    expect(api.fetchPostings).not.toHaveBeenCalled();
    expect(
      screen.queryByText("데이터를 불러오지 못했습니다"),
    ).not.toBeInTheDocument();
  });

  it("늦게 도착한 앞 요청이 뒤 요청의 로딩 문구를 지우지 않는다", async () => {
    let resolveMonthly!: (v: api.MonthlyOverview) => void;
    let resolvePostings!: (v: api.PostingPage) => void;
    vi.mocked(api.fetchMonthlyOverview).mockReturnValue(
      new Promise((r) => {
        resolveMonthly = r;
      }),
    );
    vi.mocked(api.fetchPostings).mockReturnValue(
      new Promise((r) => {
        resolvePostings = r;
      }),
    );
    renderAt("/admin/data?tab=monthly");
    await screen.findByText(LOADING);
    await userEvent.click(screen.getByRole("tab", { name: "공고" }));
    await act(async () => resolveMonthly(monthly));
    expect(screen.getByText(LOADING)).toBeInTheDocument();
    await act(async () => resolvePostings(postings));
    expect(screen.queryByText(LOADING)).not.toBeInTheDocument();
    expect(screen.getByText("QA 채용")).toBeInTheDocument();
  });

  it("불러오지 못하면 재시도를 보인다", async () => {
    vi.mocked(api.fetchMonthlyOverview).mockRejectedValue({
      code: "X",
      message: "x",
      status: 500,
    });
    renderAt("/admin/data");
    expect(
      await screen.findByText("데이터를 불러오지 못했습니다"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toHaveAttribute(
      "data-variant",
      "outline",
    );
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

  it("회사는 입력만으로는 조회하지 않고 Enter 로 URL 파라미터에 넣어 부른다", async () => {
    renderAt("/admin/data?tab=postings&month=202607");
    await screen.findByText("QA 채용");
    await userEvent.type(screen.getByLabelText("회사"), "평화");
    expect(api.fetchPostings).toHaveBeenCalledTimes(1);
    await userEvent.keyboard("{Enter}");
    await waitFor(() =>
      expect(api.fetchPostings).toHaveBeenLastCalledWith(
        expect.objectContaining({ month: "202607", company: "평화", page: 0 }),
      ),
    );
  });

  it("403 이면 관리자 키 모달을 열고 실패 알림은 띄우지 않는다", async () => {
    vi.mocked(api.fetchMonthlyOverview).mockRejectedValue({
      code: "FORBIDDEN",
      message: "x",
      status: 403,
    });
    renderAt("/admin/data");
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(
      screen.queryByText("데이터를 불러오지 못했습니다"),
    ).not.toBeInTheDocument();
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

  it("조건 초기화는 공고 탭의 조건을 지우고 기간은 남긴다", async () => {
    renderAt(
      "/admin/data?tab=postings&month=202607&source=WANTED&company=평화&from=202401",
    );
    await screen.findByText("QA 채용");
    await userEvent.click(screen.getByRole("button", { name: "조건 초기화" }));
    await waitFor(() =>
      expect(api.fetchPostings).toHaveBeenLastCalledWith(
        expect.objectContaining({ month: undefined, source: undefined }),
      ),
    );
    expect(screen.getByLabelText("회사")).toHaveValue("");
  });

  it("탭을 바꿔도 기간은 남고 나머지 조건은 지운다", async () => {
    vi.mocked(api.fetchCpi).mockResolvedValue({
      codes: ["A"],
      months: [{ month: "202609", values: { A: 118.3 } }],
      missing: [],
    });
    renderAt(
      "/admin/data?tab=monthly&from=202401&to=202609&sampleSource=WANTED",
    );
    await screen.findByText("150");
    await userEvent.click(screen.getByRole("tab", { name: "CPI" }));
    await screen.findByText("118.30");
    expect(api.fetchCpi).toHaveBeenCalledWith("202401", "202609");
    await userEvent.click(screen.getByRole("tab", { name: "월별 현황" }));
    await screen.findByText("150");
    expect(api.fetchMonthlyOverview).toHaveBeenLastCalledWith(
      "202401",
      "202609",
      undefined,
    );
  });

  it.each([
    ["cpi", () => vi.mocked(api.fetchCpi)],
    ["wage", () => vi.mocked(api.fetchWage)],
  ] as const)(
    "%s 탭도 실패하면 같은 자리에 재시도를 보이고 다시 부른다",
    async (tab, fetcher) => {
      fetcher().mockRejectedValue({ status: 500 });
      renderAt(`/admin/data?tab=${tab}`);
      const panel = await screen.findByRole("tabpanel");
      expect(
        await within(panel).findByText("데이터를 불러오지 못했습니다"),
      ).toBeInTheDocument();
      await userEvent.click(
        within(panel).getByRole("button", { name: "다시 시도" }),
      );
      await waitFor(() => expect(fetcher()).toHaveBeenCalledTimes(2));
    },
  );

  it("늦게 도착한 CPI 응답이 임금통계 탭을 덮어쓰지 않는다", async () => {
    let resolveCpi!: (v: api.CpiTable) => void;
    vi.mocked(api.fetchCpi).mockReturnValue(
      new Promise((r) => {
        resolveCpi = r;
      }),
    );
    vi.mocked(api.fetchWage).mockResolvedValue({
      basePeriod: null,
      families: [],
      brackets: [],
      cells: [],
      missing: [],
      engineBaselines: [],
    });
    renderAt("/admin/data?tab=cpi");
    await screen.findByText(LOADING);
    await userEvent.click(screen.getByRole("tab", { name: "임금통계" }));
    await screen.findByText("해당 조건의 데이터가 없습니다");
    await act(async () =>
      resolveCpi({
        codes: ["A"],
        months: [{ month: "202601", values: { A: 1 } }],
        missing: [],
      }),
    );
    expect(screen.queryByTestId("cpi-202601-A")).not.toBeInTheDocument();
  });

  it("바뀐 것 없이 칸을 벗어나면 다시 조회하지 않는다", async () => {
    renderAt("/admin/data?tab=postings&month=202607&company=평화");
    await screen.findByText("QA 채용");
    fireEvent.blur(screen.getByLabelText("회사"));
    fireEvent.blur(screen.getByLabelText("월"));
    expect(api.fetchPostings).toHaveBeenCalledTimes(1);
  });
});
