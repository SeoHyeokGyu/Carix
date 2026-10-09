import { describe, expect, it } from "vitest";
import {
  crawlJobDataHref,
  fromMonthInput,
  toMonthInput,
  formatCollectedDate,
  formatManwon,
  formatSalaryCell,
  formatSalaryRange,
  isSharpCpiChange,
  isThinFamily,
  isThinSample,
  parseTab,
} from "./dataExplore";

describe("dataExplore", () => {
  it("CPI 전월 대비 5% 넘는 변화만 급변으로 본다", () => {
    expect(isSharpCpiChange(101.0, 87.87)).toBe(true);
    expect(isSharpCpiChange(100, 104.9)).toBe(false);
    expect(isSharpCpiChange(undefined, 90)).toBe(false);
    expect(isSharpCpiChange(90, undefined)).toBe(false);
  });

  it("unknown tab falls back to monthly", () => {
    expect(parseTab(null)).toBe("monthly");
    expect(parseTab("nope")).toBe("monthly");
    expect(parseTab("postings")).toBe("postings");
  });

  it("표본 200 미만과 직무 표본 10 미만을 강조한다", () => {
    expect(isThinSample(199)).toBe(true);
    expect(isThinSample(200)).toBe(false);
    expect(isThinFamily(9)).toBe(true);
    expect(isThinFamily(10)).toBe(false);
  });

  it("원 단위 금액을 만원 단위로 보인다", () => {
    expect(formatManwon(61_234_567)).toBe("6,123만원");
    expect(formatManwon(26_920_000)).toBe("2,692만원");
  });

  it("제시 연봉 범위는 있는 쪽만, 둘 다 없으면 미공개", () => {
    expect(formatSalaryRange(50_000_000, 70_000_000)).toBe(
      "5,000만원~7,000만원",
    );
    expect(formatSalaryRange(50_000_000, null)).toBe("5,000만원~");
    expect(formatSalaryRange(null, 70_000_000)).toBe("~7,000만원");
    expect(formatSalaryRange(60_000_000, 60_000_000)).toBe("6,000만원");
    expect(formatSalaryRange(null, null)).toBe("미공개");
  });
});

describe("formatSalaryCell", () => {
  const none = {
    salaryMin: null,
    salaryMax: null,
    salaryBandMin: null,
    salaryBandMax: null,
  };
  it("공개 연봉은 그대로, 비공개 구간은 흐리게", () => {
    expect(
      formatSalaryCell({
        ...none,
        salaryMin: 50_000_000,
        salaryMax: 70_000_000,
        salaryDisclosure: "PUBLIC",
      }),
    ).toEqual({ text: "5,000만원~7,000만원", muted: false });
    expect(
      formatSalaryCell({
        ...none,
        salaryBandMin: 25_900_000,
        salaryBandMax: 100_000_000,
        salaryDisclosure: "BAND",
      }),
    ).toEqual({ text: "비공개 (2,590만원~1억원)", muted: true });
    expect(formatSalaryCell({ ...none, salaryDisclosure: "NONE" })).toEqual({
      text: "미공개",
      muted: true,
    });
    expect(formatSalaryCell({ ...none, salaryDisclosure: "UNKNOWN" })).toEqual({
      text: "판정 전",
      muted: true,
    });
    expect(
      formatSalaryCell({
        ...none,
        salaryMin: 50_000_000,
        salaryMax: 60_000_000,
        salaryDisclosure: "UNKNOWN",
      }).text,
    ).toBe("판정 전 (5,000만원~6,000만원)");
    expect(
      formatSalaryCell({
        ...none,
        salaryBandMin: 40_000_000,
        salaryBandMax: 70_000_000,
        salaryDisclosure: "UNKNOWN",
      }).text,
    ).toBe("판정 전 (4,000만원~7,000만원)");
  });

  it("1억 이상 금액 표기", () => {
    expect(formatManwon(120_000_000)).toBe("1억 2,000만원");
  });
});

describe("crawlJobDataHref", () => {
  const base = {
    jobType: "CRAWL",
    status: "SUCCESS",
    startedAt: "2026-10-07T01:00:00Z",
  };

  it("성공한 크롤링은 시작월·포털 원천으로 공고 탭 링크를 만든다", () => {
    expect(crawlJobDataHref({ ...base, details: { portal: "JOBKOREA" } })).toBe(
      "/admin/data?tab=postings&month=202610&sort=recent&source=JOBKOREA",
    );
    expect(
      crawlJobDataHref({ ...base, details: { portal: "SARAMIN" } }),
    ).toContain("source=SARAMIN");
  });

  it("원티드는 원티드 원천으로 연다", () => {
    expect(crawlJobDataHref({ ...base, details: { portal: "WANTED" } })).toBe(
      "/admin/data?tab=postings&month=202610&sort=recent&source=WANTED",
    );
  });

  it("점핏은 사람인 원천으로, 전체는 원천 없이 연다", () => {
    expect(crawlJobDataHref({ ...base, details: { portal: "JUMPIT" } })).toBe(
      "/admin/data?tab=postings&month=202610&sort=recent&source=SARAMIN",
    );
    expect(crawlJobDataHref({ ...base, details: { portal: "ALL" } })).toBe(
      "/admin/data?tab=postings&month=202610&sort=recent",
    );
    expect(crawlJobDataHref({ ...base, details: null })).toBe(
      "/admin/data?tab=postings&month=202610&sort=recent",
    );
  });

  it("월은 collector 와 같은 UTC 기준이다", () => {
    expect(
      crawlJobDataHref({ ...base, startedAt: "2026-10-31T16:00:00Z" }),
    ).toContain("month=202610");
    expect(
      crawlJobDataHref({ ...base, startedAt: "2026-11-01T00:30:00+09:00" }),
    ).toContain("month=202610");
  });

  it("크롤링이 아니거나 성공이 아니면 링크가 없다", () => {
    expect(crawlJobDataHref({ ...base, status: "FAILED" })).toBeNull();
    expect(crawlJobDataHref({ ...base, jobType: "CPI_INGEST" })).toBeNull();
  });
});

describe("formatCollectedDate", () => {
  it("서울 날짜로 보여 준다", () => {
    expect(formatCollectedDate("2026-09-30T16:00:00Z")).toBe("2026-10-01");
  });
});

describe("월 입력 변환", () => {
  it("yyyyMM 과 yyyy-MM 을 서로 바꾼다", () => {
    expect(toMonthInput("202610")).toBe("2026-10");
    expect(toMonthInput(undefined)).toBe("");
    expect(toMonthInput("2026")).toBe("");
    expect(fromMonthInput("2026-10")).toBe("202610");
    expect(fromMonthInput("")).toBeUndefined();
  });
});
