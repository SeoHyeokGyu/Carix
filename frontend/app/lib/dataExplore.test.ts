import { describe, expect, it } from "vitest";

import type { WageTable } from "./collectorApi";
import {
  crawlJobDataHref,
  formatCollectedDate,
  formatManwon,
  formatSalaryCell,
  formatSalaryRange,
  fromMonthInput,
  isSharpCpiChange,
  isThinFamily,
  isThinSample,
  isWageUniformAcrossFamilies,
  parseTab,
  periodRangeOf,
  tabSwitchParams,
  toMonthInput,
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

describe("periodRangeOf", () => {
  it("응답 월의 가장 이른 월과 가장 늦은 월을 돌린다 — 순서를 믿지 않는다", () => {
    expect(
      periodRangeOf([
        { month: "202609" },
        { month: "202411" },
        { month: "202512" },
      ]),
    ).toEqual({ from: "202411", to: "202609" });
  });

  it("월이 없으면 undefined", () => {
    expect(periodRangeOf([])).toBeUndefined();
  });
});

describe("tabSwitchParams", () => {
  it("탭만 바꾸고 기간은 남기며 나머지 조건은 지운다", () => {
    const next = tabSwitchParams(
      "cpi",
      new URLSearchParams(
        "tab=monthly&from=202401&to=202609&sampleSource=WANTED",
      ),
    );
    expect(next.toString()).toBe("tab=cpi&from=202401&to=202609");
  });

  it("기간이 없으면 탭만 남는다", () => {
    const next = tabSwitchParams(
      "monthly",
      new URLSearchParams("tab=postings&month=202607&source=WANTED"),
    );
    expect(next.toString()).toBe("tab=monthly");
  });
});

const gross = (wageMedian: number) => ({
  wageDefinition: "ANNUAL_GROSS_MEDIAN",
  source: "KOSIS",
  statVintage: "202508",
  wageMedian,
});

function wageTable(overrides: Partial<WageTable> = {}): WageTable {
  return {
    basePeriod: "2025",
    families: ["DEVELOPER", "DATA"],
    brackets: ["ENTRY", "MID"],
    cells: ["DEVELOPER", "DATA"].flatMap((f) => [
      {
        jobFamily: f,
        experienceBracket: "ENTRY",
        sources: ["KOSIS"],
        values: [gross(32_240_000)],
      },
      {
        jobFamily: f,
        experienceBracket: "MID",
        sources: ["KOSIS"],
        values: [gross(47_690_000)],
      },
    ]),
    missing: [],
    engineBaselines: [],
    ...overrides,
  };
}

describe("isWageUniformAcrossFamilies", () => {
  it("모든 직무의 칸 값이 같으면 참", () => {
    expect(isWageUniformAcrossFamilies(wageTable())).toBe(true);
  });

  it("값이 칸 순서만 달라도 같다고 본다", () => {
    const t = wageTable();
    const reordered = {
      ...t,
      cells: t.cells.map((c) =>
        c.jobFamily === "DATA"
          ? {
              ...c,
              values: [
                ...c.values,
                { ...gross(1), wageDefinition: "ANNUAL_BASE_MEDIAN" },
              ].reverse(),
            }
          : {
              ...c,
              values: [
                ...c.values,
                { ...gross(1), wageDefinition: "ANNUAL_BASE_MEDIAN" },
              ],
            },
      ),
    };
    expect(isWageUniformAcrossFamilies(reordered)).toBe(true);
  });

  it("한 직무의 값이 다르면 거짓", () => {
    const t = wageTable();
    const differs = {
      ...t,
      cells: t.cells.map((c) =>
        c.jobFamily === "DATA" && c.experienceBracket === "MID"
          ? { ...c, values: [gross(50_000_000)] }
          : c,
      ),
    };
    expect(isWageUniformAcrossFamilies(differs)).toBe(false);
  });

  it("한 직무에만 빈 칸이 있으면 거짓", () => {
    const t = wageTable();
    expect(
      isWageUniformAcrossFamilies({
        ...t,
        cells: t.cells.filter(
          (c) => !(c.jobFamily === "DATA" && c.experienceBracket === "MID"),
        ),
      }),
    ).toBe(false);
  });

  it("엔진 기준선이 직무마다 다르면 거짓", () => {
    const baseline = (jobFamily: string, wageMedian: number) => ({
      jobFamily,
      experienceBracket: "MID",
      region: "ALL",
      basePeriod: "2025",
      wageDefinition: "ANNUAL_GROSS_MEDIAN",
      source: "KOSIS",
      statVintage: "202508",
      wageMedian,
    });
    expect(
      isWageUniformAcrossFamilies(
        wageTable({
          engineBaselines: [
            baseline("DEVELOPER", 47_690_000),
            baseline("DATA", 48_000_000),
          ],
        }),
      ),
    ).toBe(false);
    expect(
      isWageUniformAcrossFamilies(
        wageTable({
          engineBaselines: [
            baseline("DEVELOPER", 47_690_000),
            baseline("DATA", 47_690_000),
          ],
        }),
      ),
    ).toBe(true);
  });

  it("직무가 하나뿐이면 접을 것이 없으므로 거짓", () => {
    expect(
      isWageUniformAcrossFamilies(wageTable({ families: ["DEVELOPER"] })),
    ).toBe(false);
  });
});
