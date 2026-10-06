import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub, Link } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DataExplorePage from "./data";
import * as api from "../../lib/collectorApi";

vi.mock("../../lib/collectorApi", async (orig) => ({
  ...(await orig<typeof import("../../lib/collectorApi")>()),
  fetchMonthlyOverview: vi.fn(),
  fetchPostings: vi.fn(),
  fetchCpi: vi.fn(),
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
      sampledByFamily: { DEVELOPER: 200, DATA: 9, AI: 35, OTHER: 0 },
    },
    {
      month: "202609",
      jobkoreaSampled: 150,
      jobkoreaSampledCapped: 148,
      jobkoreaCrawled: 1248,
      saraminCrawled: 410,
      sampledByFamily: { DEVELOPER: 120, DATA: 10, AI: 18, OTHER: 0 },
    },
  ],
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
      skills: ["QA"],
    },
  ],
};

function PageWithBackLink() {
  return (
    <>
      <Link to="/admin/data?tab=postings&month=202607">공고로 돌아가기</Link>
      <DataExplorePage />
    </>
  );
}

function renderAt(url: string) {
  const Stub = createRoutesStub([
    { path: "/admin/data", Component: PageWithBackLink },
  ]);
  render(<Stub initialEntries={[url]} />);
}

describe("DataExplorePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.fetchMonthlyOverview).mockResolvedValue(monthly);
    vi.mocked(api.fetchPostings).mockResolvedValue(postings);
  });

  it("기본 탭은 월별 현황이고 얇은 칸을 강조한다", async () => {
    renderAt("/admin/data");
    const thin = await screen.findByText("150");
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
    expect(api.fetchMonthlyOverview).toHaveBeenCalledWith("202310", "202609");
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
});
