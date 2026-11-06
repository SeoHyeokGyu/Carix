import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";

import type { WageGrowthResponse } from "../../lib/dashboardMarketApi";
import { WageGrowthSection } from "./WageGrowthSection";

const width = vi.hoisted(() => ({ value: 640 }));

vi.mock("../../hooks/useElementWidth", () => ({
  useElementWidth: () => [vi.fn(), width.value],
}));

const BASE: WageGrowthResponse = {
  status: "OK",
  bracket: "MID",
  basis: "AGE_BAND_PROXY",
  source: "KOSIS",
  firstStatYear: "2020",
  latestStatYear: "2025",
  lastRecordedYear: "2025",
  latest: {
    fromYear: "2021",
    toYear: "2025",
    myGrowthPercent: 12.5,
    marketGrowthPercent: 13.3,
    gapPp: -0.8,
  },
  runs: [
    {
      points: [
        {
          year: "2021",
          myIndex: 100,
          marketIndex: 100,
          recorded: true,
          myGross: 40_000_000,
          marketMedian: 10_000_000,
        },
        {
          year: "2023",
          myIndex: 106.3,
          marketIndex: 106.4,
          recorded: true,
          myGross: 42_500_000,
          marketMedian: 10_640_000,
        },
        {
          year: "2024",
          myIndex: 106.3,
          marketIndex: 110.2,
          recorded: false,
          myGross: null,
          marketMedian: 11_020_000,
        },
        {
          year: "2025",
          myIndex: 112.5,
          marketIndex: 113.3,
          recorded: true,
          myGross: 45_000_000,
          marketMedian: 11_330_000,
        },
      ],
      spans: [
        {
          fromYear: "2021",
          toYear: "2023",
          myGrowthPercent: 6.3,
          marketGrowthPercent: 6.4,
        },
        {
          fromYear: "2023",
          toYear: "2025",
          myGrowthPercent: 5.9,
          marketGrowthPercent: 6.5,
        },
      ],
    },
  ],
};

function withLatest(
  myGrowthPercent: number,
  gapPp: number,
  extra: Partial<WageGrowthResponse> = {},
): WageGrowthResponse {
  return {
    ...BASE,
    ...extra,
    latest: { ...BASE.latest!, myGrowthPercent, gapPp },
  };
}

function notOk(
  status: WageGrowthResponse["status"],
  latestStatYear: string | null = "2025",
): WageGrowthResponse {
  return {
    ...BASE,
    status,
    firstStatYear: latestStatYear ? "2020" : null,
    latestStatYear,
    latest: null,
    runs: [],
  };
}

function renderSection(data: WageGrowthResponse) {
  render(
    <MemoryRouter>
      <WageGrowthSection data={data} />
    </MemoryRouter>,
  );
}

const SIDE_EFFECT =
  "과거 연봉을 넣으면 구매력 지수 기준월이 앞당겨져 지표가 달라질 수 있어요. 상여를 따로 받았다면 상여 조건도 함께 입력해 주세요.";

describe("WageGrowthSection", () => {
  it("격차가 구간 밖으로 낮으면 못 미친다고 말한다", () => {
    renderSection(withLatest(11.8, -1.5));

    expect(
      screen.getByText(
        "같은 경력 구간 임금이 13.3% 오르는 동안 내 연봉은 11.8% 올랐습니다",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("시장 기본 상승분에 1.5%p 못 미칩니다 · 2021→2025"),
    ).toBeInTheDocument();
  });

  it.each([[-1], [0], [1]])("격차 %s 는 비슷하다고 말한다", (gap) => {
    renderSection(withLatest(13.3 + gap, gap));

    expect(
      screen.getByText("시장 기본 상승분과 비슷합니다 · 2021→2025"),
    ).toBeInTheDocument();
  });

  it("격차가 구간 밖으로 높아도 앞섰다고 하지 않고 연차 효과를 밝힌다", () => {
    renderSection(withLatest(14.4, 1.1));

    expect(
      screen.getByText(
        "내 연봉에는 연차 상승분도 들어 있어 시장을 앞섰다고 단정할 수는 없습니다 · 2021→2025",
      ),
    ).toBeInTheDocument();
  });

  // 이직 등으로 연봉이 줄 수 있다. 「-3.0% 올랐습니다」는 뜻이 뒤집힌다.
  it("내 연봉이 줄면 줄었다고 말한다", () => {
    renderSection(withLatest(-3, -16.3));

    expect(
      screen.getByText(
        "같은 경력 구간 임금이 13.3% 오르는 동안 내 연봉은 3.0% 줄었습니다",
      ),
    ).toBeInTheDocument();
  });

  it("내 연봉이 그대로면 그대로라고 말한다", () => {
    renderSection(withLatest(0, -13.3));

    expect(
      screen.getByText(
        "같은 경력 구간 임금이 13.3% 오르는 동안 내 연봉은 그대로였습니다",
      ),
    ).toBeInTheDocument();
  });

  it("시장 임금이 줄면 줄어드는 동안이라고 말한다", () => {
    renderSection({
      ...BASE,
      latest: { ...BASE.latest!, marketGrowthPercent: -1 },
    });

    expect(
      screen.getByText(
        "같은 경력 구간 임금이 1.0% 줄어드는 동안 내 연봉은 12.5% 올랐습니다",
      ),
    ).toBeInTheDocument();
  });

  it("비교가 일찍 끝나고 그 뒤 기록이 없으면 입력을 안내한다", () => {
    renderSection({
      ...BASE,
      lastRecordedYear: "2023",
      latest: { ...BASE.latest!, toYear: "2023" },
    });

    expect(
      screen.getByText(
        "2023년까지만 비교했습니다. 2025년까지의 연봉을 입력하면 더 볼 수 있어요",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("link")).toHaveAttribute("href", "/mypage/records");
  });

  it("기록은 더 있지만 비교가 일찍 끝나면 비교 가능 범위만 알린다", () => {
    renderSection({
      ...BASE,
      lastRecordedYear: "2025",
      latest: { ...BASE.latest!, toYear: "2022" },
    });

    expect(
      screen.getByText("2022년까지만 비교할 수 있습니다"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("비교 끝 연도가 최신 통계와 같으면 안내가 없다", () => {
    renderSection({ ...BASE, lastRecordedYear: "2026" });

    expect(screen.queryByText(/까지만 비교/)).not.toBeInTheDocument();
  });

  it.each(["NO_INCOME_HISTORY", "NO_OVERLAP"] as const)(
    "%s 는 통계 연도와 입력 부작용을 안내한다",
    (status) => {
      renderSection(notOk(status));

      expect(
        screen.getByText(
          "2020~2025년 연봉 기록이 두 해 이상 있어야 비교할 수 있습니다",
        ),
      ).toBeInTheDocument();
      expect(screen.getByText(SIDE_EFFECT)).toBeInTheDocument();
      expect(screen.getByRole("link")).toHaveAttribute(
        "href",
        "/mypage/records",
      );
    },
  );

  it("통계 연도가 없으면 연도를 뺀 문구를 쓴다", () => {
    renderSection(notOk("NO_INCOME_HISTORY", null));

    expect(
      screen.getByText("연봉 기록이 두 해 이상 있어야 비교할 수 있습니다"),
    ).toBeInTheDocument();
  });

  it("NO_WAGE_STAT 은 결측 문구만 쓰고 차트와 수치를 그리지 않는다", () => {
    renderSection(notOk("NO_WAGE_STAT"));

    expect(screen.getByText("공식 임금통계 결측 구간")).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("빈 해를 합친 표 행과 기준선 성격·출처를 보인다", () => {
    renderSection(BASE);

    expect(screen.getByRole("cell", { name: "2023→25" })).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "+5.9%" })).toBeInTheDocument();
    expect(
      screen.getByText(/연령대 기반 추정, 전 직무 공통/),
    ).toBeInTheDocument();
    expect(screen.getByText(/KOSIS/)).toBeInTheDocument();
  });

  it("좁은 폭에서 선 끝 라벨을 빼고 범례는 유지한다", () => {
    width.value = 300;
    renderSection(BASE);
    width.value = 640;

    expect(screen.queryByText("시장 113.3")).not.toBeInTheDocument();
    expect(screen.getByText("같은 경력 구간 임금 중앙값")).toBeInTheDocument();
  });

  // 눈금이 21개면 232px 높이에서 라벨이 겹친다
  it("지수 범위가 넓어도 눈금 수를 제한하고 기준 100 은 눈금에 남긴다", () => {
    const wide: WageGrowthResponse = {
      ...BASE,
      runs: [
        {
          points: [
            { ...BASE.runs[0].points[0] },
            { ...BASE.runs[0].points[3], myIndex: 300 },
          ],
          spans: [],
        },
      ],
    };

    renderSection(wide);

    const svg = screen.getByRole("img");
    const ticks = [...svg.querySelectorAll("text")]
      .map((t) => t.textContent ?? "")
      .filter((t) => /^\d{1,3}$/.test(t));
    expect(ticks.length).toBeLessThanOrEqual(9);
    expect(ticks).toContain("100");
  });

  it("넓은 폭에서는 선 끝에 값을 적는다", () => {
    renderSection(BASE);

    expect(screen.getByText("시장 113.3")).toBeInTheDocument();
    expect(screen.getByText("내 연봉 112.5")).toBeInTheDocument();
  });
});
