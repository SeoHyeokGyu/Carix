import { Link } from "react-router";
import type { SnapshotPoint } from "../../lib/dashboardMarketApi";
import { toSparklineSeries } from "../../lib/verdict";
import { Sparkline } from "./Sparkline";

/** 점 하나는 추세가 아니다. */
const MIN_POINTS = 2;

/**
 * 시장가치 카드(2층) 안의 최근 추세. 판정(1층)이 아니라 이 지표의 근거라 카드에 둔다.
 * 선만으로는 크기를 못 읽으므로 시작·끝 값을 숫자로, 끊긴 달은 개수로 함께 보인다.
 */
export function MarketValueTrend({
  history,
}: {
  history: readonly SnapshotPoint[];
}) {
  const series = toSparklineSeries(history);
  const points = series.segments.flat().sort((a, b) => a.index - b.index);
  if (points.length < MIN_POINTS) return null;

  const first = points[0].value.toFixed(1);
  const last = points[points.length - 1].value.toFixed(1);
  const missing = series.slots - points.length;
  const period = `최근 ${series.slots}개월`;

  return (
    <div className="flex flex-col gap-1.5 border-t border-carix-border pt-3">
      <div className="flex items-baseline justify-between gap-2 text-xs">
        <span className="text-carix-text/60">{period}</span>
        <span aria-hidden="true" className="font-medium text-carix-text">
          {`${first} → ${last}`}
        </span>
      </div>
      <Sparkline series={series} />
      <p className="sr-only">
        {/* 「~으로/로」는 숫자 끝자리 받침에 따라 갈린다 — 조사가 안 바뀌는 「까지」를 쓴다. */}
        {`${period} 시장가치 지수가 ${first}에서 ${last}까지 변했습니다.`}
        {missing > 0 && ` 값이 없는 달 ${missing}개월.`}
      </p>
      <div className="flex items-center justify-between gap-2 text-[11px]">
        {missing > 0 ? (
          <span className="text-carix-text/50">
            {`값 없는 달 ${missing}개월 (수집 공백·표본 부족)`}
          </span>
        ) : (
          <span />
        )}
        <Link
          to="/dashboard/history"
          className="font-medium text-carix-info hover:underline"
        >
          지난 판정 →
        </Link>
      </div>
    </div>
  );
}
