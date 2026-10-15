import { describe, expect, it } from "vitest";
import type { SnapshotPoint } from "./dashboardMarketApi";
import {
  countSignalStreak,
  formatVerdictDescription,
  previousYearMonth,
  toSparklineSeries,
} from "./verdict";

function point(
  baseYearMonth: string,
  decisionSignal: string,
  marketValueIndex: number | null = 100,
): SnapshotPoint {
  return {
    baseYearMonth,
    purchasingPowerIndex: null,
    marketValueIndex,
    decisionSignal,
    label: "",
    dataVersion: "",
    ruleVersion: "",
    rationale: null,
    recorded: null,
    changed: false,
  };
}

describe("formatVerdictDescription", () => {
  it("기준일의 연·월로 제목을 만든다", () => {
    expect(formatVerdictDescription("2026-08-01")).toBe(
      "2026년 8월 기준 · 내 커리어 판정",
    );
  });

  it("기준일이 없거나 형식이 아니면 월 없이 쓴다", () => {
    expect(formatVerdictDescription(undefined)).toBe("내 커리어 판정");
    expect(formatVerdictDescription("-")).toBe("내 커리어 판정");
  });
});

describe("previousYearMonth", () => {
  it("1월의 앞 달은 전년 12월이다", () => {
    expect(previousYearMonth("202601")).toBe("202512");
    expect(previousYearMonth("202608")).toBe("202607");
  });

  it("형식이 아니면 null", () => {
    expect(previousYearMonth("2026-08")).toBeNull();
  });
});

describe("countSignalStreak", () => {
  it("최신 달부터 같은 코드가 이어진 개월만 센다", () => {
    const history = [
      point("202608", "STAY"),
      point("202607", "STAY"),
      point("202606", "NEGOTIATE"),
      point("202605", "STAY"),
    ];
    expect(countSignalStreak(history, "STAY")).toBe(2);
  });

  it("이력이 1개월뿐이면 1", () => {
    expect(countSignalStreak([point("202608", "STAY")], "STAY")).toBe(1);
  });

  it("이력이 없으면 0", () => {
    expect(countSignalStreak([], "STAY")).toBe(0);
  });

  it("최신 달이 다른 코드면 0", () => {
    expect(countSignalStreak([point("202608", "NEGOTIATE")], "STAY")).toBe(0);
  });

  it("달력상 빈 달이 있으면 거기서 끊는다", () => {
    const history = [
      point("202608", "STAY"),
      point("202606", "STAY"),
      point("202605", "STAY"),
    ];
    expect(countSignalStreak(history, "STAY")).toBe(1);
  });

  it("연도를 넘어 이어진다", () => {
    const history = [point("202601", "STAY"), point("202512", "STAY")];
    expect(countSignalStreak(history, "STAY")).toBe(2);
  });
});

describe("toSparklineSeries", () => {
  it("오래된 달이 왼쪽이 되게 뒤집는다", () => {
    const series = toSparklineSeries([
      point("202608", "STAY", 104),
      point("202607", "STAY", 102),
    ]);
    expect(series.slots).toBe(2);
    expect(series.segments).toEqual([
      [
        { index: 0, value: 102 },
        { index: 1, value: 104 },
      ],
    ]);
  });

  it("null 과 0 인 달에서 선을 끊고 잇지 않는다", () => {
    const series = toSparklineSeries([
      point("202608", "STAY", 104),
      point("202607", "STAY", 102),
      point("202606", "STAY", 0),
      point("202605", "STAY", null),
      point("202604", "STAY", 100),
    ]);
    expect(series.slots).toBe(5);
    expect(series.segments).toEqual([
      [{ index: 0, value: 100 }],
      [
        { index: 3, value: 102 },
        { index: 4, value: 104 },
      ],
    ]);
  });

  it("최근 months 개월만 본다", () => {
    const history = Array.from({ length: 15 }, (_, i) =>
      point(`2026${String(15 - i).padStart(2, "0")}`, "STAY", 100 + i),
    );
    const series = toSparklineSeries(history, 12);
    expect(series.slots).toBe(12);
    // 보존된 값들은 최신 12개월이다 (202615-202604, values 100-111)
    const lastSegment = series.segments[series.segments.length - 1];
    expect(lastSegment[lastSegment.length - 1].value).toBe(100);
  });

  it("달력상 빈 달을 인덱스 간격으로 감지한다", () => {
    const series = toSparklineSeries([
      point("202608", "STAY", 104),
      point("202606", "STAY", 102),
    ]);
    expect(series.slots).toBe(3);
    expect(series.segments).toEqual([
      [{ index: 0, value: 102 }],
      [{ index: 2, value: 104 }],
    ]);
  });

  it("빈 snapshots 는 빈 결과를 반환한다", () => {
    const series = toSparklineSeries([]);
    expect(series.slots).toBe(0);
    expect(series.segments).toEqual([]);
  });

  it("오래된 달이 null이면 slots은 여전히 모든 포함된 달을 센다", () => {
    const series = toSparklineSeries([
      point("202608", "STAY", 104),
      point("202607", "STAY", 102),
      point("202606", "STAY", null),
    ]);
    expect(series.slots).toBe(3);
    expect(series.segments).toEqual([
      [
        { index: 1, value: 102 },
        { index: 2, value: 104 },
      ],
    ]);
  });

  it("오래된 달이 0이면 slots은 여전히 모든 포함된 달을 센다", () => {
    const series = toSparklineSeries([
      point("202608", "STAY", 104),
      point("202607", "STAY", 102),
      point("202606", "STAY", 0),
    ]);
    expect(series.slots).toBe(3);
    expect(series.segments).toEqual([
      [
        { index: 1, value: 102 },
        { index: 2, value: 104 },
      ],
    ]);
  });
});
