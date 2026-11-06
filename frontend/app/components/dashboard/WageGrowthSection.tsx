import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { TextLink } from "@/components/ui/text-link";

import type {
  WageGrowthLatest,
  WageGrowthResponse,
  WageGrowthSpan,
} from "../../lib/dashboardMarketApi";
import { WageGrowthChart } from "./WageGrowthChart";

/**
 * 이 안의 격차는 `비슷`으로 말한다 (%p). 시장 값이 연령대 프록시 보간이라 작은 차이로 뒤처졌다고 말하면 잡음을 말하게 된다.
 * 운영 임금통계에서 구간별 전년 대비 상승률이 전체 상승률과 같은 해 평균 1%p 쯤 벌어졌다. 누적은 양 끝점만 쓰므로 비교 기간과 무관하게 같은 값이다.
 */
const SIMILAR_BAND_PP = 1;

const INCOME_RECORDS_PATH = "/mypage/records";
const HISTORY_SIDE_EFFECT =
  "과거 연봉을 넣으면 구매력 지수 기준월이 앞당겨져 지표가 달라질 수 있어요. 상여를 따로 받았다면 상여 조건도 함께 입력해 주세요.";

function percent(value: number): string {
  return `${value.toFixed(1)}%`;
}

function signedPercent(value: number): string {
  return `${value > 0 ? "+" : ""}${percent(value)}`;
}

function verdict(latest: WageGrowthLatest): string {
  const period = `${latest.fromYear}→${latest.toYear}`;
  if (latest.gapPp < -SIMILAR_BAND_PP) {
    return `시장 기본 상승분에 ${Math.abs(latest.gapPp).toFixed(1)}%p 못 미칩니다 · ${period}`;
  }
  if (latest.gapPp > SIMILAR_BAND_PP) {
    return `내 연봉에는 연차 상승분도 들어 있어 시장을 앞섰다고 단정할 수는 없습니다 · ${period}`;
  }
  return `시장 기본 상승분과 비슷합니다 · ${period}`;
}

function headline(latest: WageGrowthLatest): string {
  const { marketGrowthPercent: market, myGrowthPercent: mine } = latest;
  const marketPart =
    market < 0
      ? `${percent(Math.abs(market))} 줄어드는 동안`
      : `${percent(market)} 오르는 동안`;
  let minePart = `${percent(mine)} 올랐습니다`;
  if (mine < 0) minePart = `${percent(Math.abs(mine))} 줄었습니다`;
  if (mine === 0) minePart = "그대로였습니다";
  return `같은 경력 구간 임금이 ${marketPart} 내 연봉은 ${minePart}`;
}

function spanLabel(span: WageGrowthSpan): string {
  return `${span.fromYear}→${span.toYear.slice(2)}`;
}

/** 끝난 지 오래된 비교가 지금 상태처럼 읽히지 않게 알린다. */
function StaleNotice({
  latest,
  lastRecordedYear,
  latestStatYear,
}: {
  latest: WageGrowthLatest;
  lastRecordedYear: string | null;
  latestStatYear: string | null;
}) {
  if (!latestStatYear || latest.toYear >= latestStatYear) return null;
  if (lastRecordedYear === latest.toYear) {
    return (
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-carix-text/80">
        <p>{`${latest.toYear}년까지만 비교했습니다. ${latestStatYear}년까지의 연봉을 입력하면 더 볼 수 있어요`}</p>
        <TextLink to={INCOME_RECORDS_PATH}>소득 이력 입력하기</TextLink>
      </div>
    );
  }
  return (
    <p className="text-xs text-carix-text/80">
      {`${latest.toYear}년까지만 비교할 수 있습니다`}
    </p>
  );
}

function NotComparable({ data }: { data: WageGrowthResponse }) {
  if (data.status === "NO_WAGE_STAT") {
    return (
      <EmptyState
        variant="plain"
        className="py-4"
        title="공식 임금통계 결측 구간"
        description="실측 통계가 이어지는 구간이 없어 상승률을 내지 않습니다."
      />
    );
  }
  const title = data.latestStatYear
    ? `${data.firstStatYear}~${data.latestStatYear}년 연봉 기록이 두 해 이상 있어야 비교할 수 있습니다`
    : "연봉 기록이 두 해 이상 있어야 비교할 수 있습니다";
  return (
    <EmptyState
      variant="plain"
      className="py-4"
      title={title}
      description={HISTORY_SIDE_EFFECT}
    >
      <TextLink to={INCOME_RECORDS_PATH}>소득 이력 입력하기</TextLink>
    </EmptyState>
  );
}

export interface WageGrowthSectionProps {
  data: WageGrowthResponse;
}

/** 내 연봉이 시장 기본 상승분을 따라갔는지 (US-D8). 판정·지수에는 연결하지 않는다. */
export function WageGrowthSection({ data }: WageGrowthSectionProps) {
  if (data.status !== "OK" || !data.latest) {
    return <NotComparable data={data} />;
  }
  const { latest, runs } = data;
  const spans = runs.flatMap((run) => run.spans);

  return (
    <Card data-testid="wage-growth-section">
      <div className="space-y-1">
        <p className="text-base font-bold text-carix-text">
          {headline(latest)}
        </p>
        <p className="text-sm text-carix-text/80">{verdict(latest)}</p>
        <StaleNotice
          latest={latest}
          lastRecordedYear={data.lastRecordedYear}
          latestStatYear={data.latestStatYear}
        />
      </div>

      <WageGrowthChart runs={runs} />

      <table className="w-full text-sm text-carix-text">
        <thead>
          <tr className="border-b border-carix-border text-xs text-carix-text/70">
            <th scope="col" className="py-1.5 text-left font-medium">
              구간
            </th>
            <th scope="col" className="py-1.5 text-right font-medium">
              내 연봉
            </th>
            <th scope="col" className="py-1.5 text-right font-medium">
              시장
            </th>
          </tr>
        </thead>
        <tbody>
          {spans.map((span) => (
            <tr
              key={span.fromYear}
              className="border-b border-carix-border/60 tabular-nums"
            >
              <td className="py-1.5">{spanLabel(span)}</td>
              <td className="py-1.5 text-right">
                {signedPercent(span.myGrowthPercent)}
              </td>
              <td className="py-1.5 text-right">
                {signedPercent(span.marketGrowthPercent)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <p className="text-xs text-carix-text/70">
        기준선: 연령대 기반 추정, 전 직무 공통 · 출처 KOSIS 고용형태별
        근로실태조사
      </p>
    </Card>
  );
}
