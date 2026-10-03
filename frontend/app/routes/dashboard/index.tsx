import { useState } from "react";
import { Link, redirect, useLoaderData, useRevalidator } from "react-router";
import { RotateCw } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { IndexCard } from "../../components/dashboard/IndexCard";
import {
  OpeningWindowHelp,
  MarketValueHelp,
  PurchasingPowerHelp,
} from "../../components/dashboard/IndexHelp";
import { PriceDriverBadge } from "../../components/dashboard/PriceDriverBadge";
import { TrustPanel } from "../../components/dashboard/TrustPanel";
import { VerdictHero } from "../../components/dashboard/VerdictHero";
import { useIsNarrowViewport } from "../../hooks/useIsNarrowViewport";
import { toUserMessage } from "../../lib/apiError";
import { getProfile } from "../../lib/api/profile";
import {
  fetchSummary,
  recalculateDashboard,
  type DashboardSummary,
} from "../../lib/api/dashboard";
import { UnauthorizedError } from "../../lib/coreApi";
import { fetchSnapshotHistory } from "../../lib/dashboardMarketApi";
import { resolvePeerConditions } from "../../lib/peerConditions";
import { LOGIN_PATH } from "../../lib/routeGuards";
import { fetchSkillRecommendations } from "../../lib/skillApi";
import { formatVerdictHeading } from "../../lib/verdict";

/**
 * 1층 프리뷰와 3층 비교 조건의 재료. 없어도 판정은 선다 — 실패하면 그 요소만 사라지고
 * 화면은 남는다. 401 만은 다른 화면과 같이 로그인으로 보낸다.
 */
async function loadOptional<T>(load: () => Promise<T>): Promise<T | null> {
  try {
    return await load();
  } catch (error) {
    if (error instanceof UnauthorizedError) throw error;
    return null;
  }
}

export async function clientLoader() {
  try {
    const [summary, history, profile, recommendations] = await Promise.all([
      fetchSummary(),
      loadOptional(fetchSnapshotHistory),
      loadOptional(getProfile),
      loadOptional(fetchSkillRecommendations),
    ]);
    return {
      summary,
      history: history?.snapshots ?? null,
      profile,
      recommendations,
    };
  } catch (error) {
    // fetchSummary 는 UnauthorizedError 만 다시 던진다(설계 §10) — 다른 화면과 같은 판단으로
    // 로그인으로 보낸다. 부가 조회 둘도 401 만 여기까지 올라온다.
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    throw error;
  }
}

/**
 * 「첫 계산 전」 판정 (설계 §7.5) — 지수 셋이 **모두** 값이 없고 `NOT_AVAILABLE` 이어야
 * 한다. "값이 다 없다"만으로 근사하면 한쪽이 표본 부족 등 다른 열화일 때도 이 상태로
 * 오판한다 — 그래서 indexStatus 조건을 값 조건과 함께 명시적으로 본다.
 *
 * 서버가 지수별 상태를 스냅샷에 저장하기 전에는 `value === null` 이면 항상
 * `NOT_AVAILABLE` 이 와서 이 조건이 공회전했다. 이제는 실제로 걸러낸다.
 */
function isFirstCalculation(data: DashboardSummary): boolean {
  return (
    data.purchasingPowerIndex.value === null &&
    data.purchasingPowerIndex.indexStatus === "NOT_AVAILABLE" &&
    data.marketValueIndex.value === null &&
    data.marketValueIndex.indexStatus === "NOT_AVAILABLE" &&
    data.openingWindowIndex.value === null &&
    data.openingWindowIndex.indexStatus === "NOT_AVAILABLE"
  );
}

export default function DashboardHome() {
  const loaderData = useLoaderData<typeof clientLoader>();
  const { summary } = loaderData;
  // 재검증 사이 loaderData 가 summary 만 가진 모양으로 올 수 있다(retention 테스트) — 없으면 없는 대로 그린다.
  const history = loaderData.history ?? null;
  const profile = loaderData.profile ?? null;
  const recommendations = loaderData.recommendations ?? null;
  const isNarrow = useIsNarrowViewport();
  const revalidator = useRevalidator();
  // loader 는 실패를 예외가 아니라 {ok:false} 로 돌려준다(설계 §10) — 하지만 React Router 는
  // 재검증마다 loaderData 를 통째로 새 값으로 바꾼다. 재조회가 실패하면 이전 성공 데이터가
  // loaderData 에서 그냥 사라지므로, 화면 상태로 따로 붙잡아 두지 않으면 "마지막 성공 데이터
  // 유지" 가 성립하지 않는다. useEffect 로 동기화하면 렌더 한 번 더 도는 캐스케이드가 생기므로
  // (react-hooks/set-state-in-effect), 렌더링 중에 이전 summary 와 비교해 바로 갱신한다
  // (React 공식 패턴 — https://react.dev/learn/you-might-not-need-an-effect).
  const [lastGood, setLastGood] = useState<DashboardSummary | null>(
    summary.ok ? summary.data : null,
  );
  const [recalculatedSummary, setRecalculatedSummary] =
    useState<DashboardSummary | null>(null);
  const [prevSummary, setPrevSummary] = useState(summary);
  if (summary !== prevSummary) {
    setPrevSummary(summary);
    if (summary.ok) {
      setLastGood(summary.data);
      setRecalculatedSummary(null);
    }
  }

  const [isRecalculating, setIsRecalculating] = useState(false);
  const [recalculateMessage, setRecalculateMessage] = useState<string | null>(
    null,
  );

  const handleRecalculate = async () => {
    setIsRecalculating(true);
    setRecalculateMessage(null);
    try {
      const res = await recalculateDashboard();
      if (res.ok) {
        setRecalculatedSummary(res.data.summary);
        setLastGood(res.data.summary);
        setRecalculateMessage(
          `${res.data.recalculatedMonths}개월치 지수가 최신 정보로 재계산되었습니다.`,
        );
        revalidator.revalidate();
      } else {
        setRecalculateMessage(toUserMessage(res.error));
      }
    } catch {
      setRecalculateMessage("재계산 요청 중 오류가 발생했습니다.");
    } finally {
      setIsRecalculating(false);
    }
  };

  const data = recalculatedSummary ?? (summary.ok ? summary.data : lastGood);
  const firstCalculation = data !== null && isFirstCalculation(data);

  return (
    <section className="mx-auto w-full max-w-5xl px-6 py-10">
      <nav
        aria-label="대시보드 바로가기"
        className="flex flex-wrap justify-end gap-4"
      >
        <Link
          to="/dashboard/guide"
          className="text-sm font-medium text-carix-info hover:underline flex items-center gap-1"
        >
          지표 가이드
        </Link>
        <Link
          to="/dashboard/history"
          className="text-sm text-carix-text/70 hover:text-carix-text underline"
        >
          월별 스냅샷 이력 보기
        </Link>
        <Link
          to="/onboarding/review"
          className="text-sm text-carix-text/70 hover:text-carix-text underline"
        >
          온보딩 검토 및 재시작
        </Link>
      </nav>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-display text-xl font-bold text-carix-text">
          {formatVerdictHeading(data?.metadata.baseDate)}
        </h1>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={isRecalculating || revalidator.state === "loading"}
          onClick={handleRecalculate}
          className="flex items-center gap-1.5"
          aria-label="지표 재계산"
        >
          <RotateCw
            className={`h-3.5 w-3.5 ${isRecalculating ? "animate-spin" : ""}`}
          />
          {isRecalculating ? "재계산 중..." : "지표 재계산"}
        </Button>
      </div>

      {recalculateMessage && (
        <div className="mt-2.5 flex items-center justify-between rounded-md bg-carix-surface px-3 py-2 text-xs text-carix-info border border-carix-border">
          <span>{recalculateMessage}</span>
          <Button
            type="button"
            variant="ghost"
            size="icon-xs"
            onClick={() => setRecalculateMessage(null)}
            className="ml-2 text-carix-text/50 hover:text-carix-text"
            aria-label="알림 닫기"
          >
            ✕
          </Button>
        </div>
      )}

      {!summary.ok && (
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <p role="alert" className="text-sm text-destructive">
            {toUserMessage(summary.error)}
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={revalidator.state === "loading"}
            onClick={() => revalidator.revalidate()}
          >
            다시 시도
          </Button>
        </div>
      )}

      {data && firstCalculation && (
        <div className="mt-8 rounded-xl border border-dashed border-carix-border bg-carix-surface p-5 text-sm text-carix-text/70 shadow-xs">
          <p>첫 지수를 계산하고 있습니다</p>
          <p className="mt-1 text-carix-text/50">
            계산이 끝나면 이 화면에 자동으로 표시됩니다. 잠시 후 다시 확인해
            주세요.
          </p>
        </div>
      )}

      {data && !firstCalculation && (
        <div className="mt-6 flex flex-col gap-8">
          <VerdictHero
            signal={data.decisionSignal}
            history={history}
            isNarrow={isNarrow}
            recommendations={recommendations}
          />

          <section
            aria-labelledby="evidence-title"
            className="flex flex-col gap-3"
          >
            <h3
              id="evidence-title"
              className="text-sm font-semibold text-carix-text/80"
            >
              이 판정을 만든 지표
            </h3>
            <div className="grid gap-4 md:grid-cols-3">
              <IndexCard
                title="구매력 지수"
                index={data.purchasingPowerIndex}
                baselineHint="기준 100: 물가와 동일한 소득 변화"
                helpContent={<PurchasingPowerHelp />}
                footer={
                  <PriceDriverBadge driver={data.metadata.topPriceDriver} />
                }
              />
              <IndexCard
                title="시장가치 지수"
                index={data.marketValueIndex}
                baselineHint="기준 100: 동료집단 임금 중앙값"
                helpContent={<MarketValueHelp />}
              />
              <IndexCard
                title="개방창 지수"
                index={data.openingWindowIndex}
                baselineHint="기준 100: 전체 시장 중앙 모집 기간"
                helpContent={<OpeningWindowHelp />}
                footer={
                  <OpeningWindowBadges meta={data.metadata.openingWindow} />
                }
              />
            </div>
          </section>

          <TrustPanel
            metadata={data.metadata}
            rationale={data.decisionSignal?.rationale ?? null}
            peerConditions={resolvePeerConditions(
              data.metadata.peerGroup,
              profile,
            )}
            isNarrow={isNarrow}
          />
        </div>
      )}
    </section>
  );
}

/**
 * 개방창 지수 카드의 미니 뱃지 둘 (프런트엔드 설계 Card 4).
 *
 * 문구는 **모집 기간**으로만 쓴다. 원천이 게시한 계획된 마감일이라 "충원 난이도"나
 * "자리가 비어 있다"로 해석하면 안 된다 (기능 명세 §4.5·US-D5).
 *
 * 표본 부족이면 중앙 개방일수가 없고 상시채용 비율만 남는다.
 */
function OpeningWindowBadges({
  meta,
}: {
  meta: DashboardSummary["metadata"]["openingWindow"];
}) {
  if (!meta) return null;

  return (
    <div className="flex flex-wrap gap-1.5">
      {meta.medianWindowDays !== null && (
        <Badge variant="secondary">중앙 {meta.medianWindowDays}일</Badge>
      )}
      {meta.openEndedRate !== null && (
        <Badge variant="secondary">상시 {meta.openEndedRate.toFixed(1)}%</Badge>
      )}
    </div>
  );
}
