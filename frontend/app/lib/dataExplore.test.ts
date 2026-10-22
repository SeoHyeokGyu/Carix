import { describe, expect, it } from "vitest";

import type { WageTable } from "./collectorApi";
import {
  crawlJobDataHref,
  formatManwon,
  isSharpCpiChange,
  isThinFamily,
  isThinSample,
  isWageUniformAcrossFamilies,
  periodRangeOf,
} from "./dataExplore";

describe("dataExplore", () => {
  it("CPI 전월 대비 5% 넘는 변화만 급변으로 본다", () => {
    expect(isSharpCpiChange(101.0, 87.87)).toBe(true);
    expect(isSharpCpiChange(100, 104.9)).toBe(false);
    expect(isSharpCpiChange(undefined, 90)).toBe(false);
    expect(isSharpCpiChange(90, undefined)).toBe(false);
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
});

describe("crawlJobDataHref", () => {
  const base = {
    jobType: "CRAWL",
    status: "SUCCESS",
    startedAt: "2026-10-07T01:00:00Z",
  };

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
});
