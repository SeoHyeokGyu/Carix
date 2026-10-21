import { RotateCw } from "lucide-react";
import { useEffect, useState } from "react";
import { redirect, useLoaderData, useRevalidator } from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSection } from "@/components/layout/PageSection";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { InlineAlert } from "@/components/ui/inline-alert";
import { TextLink } from "@/components/ui/text-link";

import { IndexCard } from "../../components/dashboard/IndexCard";
import {
  MarketValueHelp,
  OpeningWindowHelp,
  PurchasingPowerHelp,
} from "../../components/dashboard/IndexHelp";
import { MarketValueTrend } from "../../components/dashboard/MarketValueTrend";
import { PpiInputLine } from "../../components/dashboard/PpiInputLine";
import { PriceDriverBadge } from "../../components/dashboard/PriceDriverBadge";
import { TrustPanel } from "../../components/dashboard/TrustPanel";
import { VerdictHero } from "../../components/dashboard/VerdictHero";
import {
  type DashboardSummary,
  fetchSummary,
  recalculateDashboard,
} from "../../lib/api/dashboard";
import { getAppliedExpenses } from "../../lib/api/expense";
import { listIncome } from "../../lib/api/income";
import { toUserMessage } from "../../lib/apiError";
import { UnauthorizedError } from "../../lib/coreApi";
import { fetchSnapshotHistory } from "../../lib/dashboardMarketApi";
import { resolvePeerConditions } from "../../lib/peerConditions";
import { toPpiInputs, toWireBaseMonth } from "../../lib/ppiInputs";
import { LOGIN_PATH } from "../../lib/routeGuards";
import { fetchSkillRecommendations } from "../../lib/skillApi";
import { trendsTabHref } from "../../lib/trendsTabs";
import { formatVerdictDescription } from "../../lib/verdict";

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
    const summaryRequest = fetchSummary();
    // 적용 소비는 판정 기준월로 물어야 해서 summary 를 기다린다 — 나머지 조회와는 나란히 돈다.
    const appliedRequest = summaryRequest.then((summary) => {
      const baseMonth = summary.ok
        ? toWireBaseMonth(summary.data.metadata.baseDate)
        : null;
      return baseMonth
        ? loadOptional(() => getAppliedExpenses(baseMonth))
        : null;
    });
    const [summary, history, recommendations, income, applied] =
      await Promise.all([
        summaryRequest,
        loadOptional(fetchSnapshotHistory),
        loadOptional(fetchSkillRecommendations),
        loadOptional(listIncome),
        appliedRequest,
      ]);
    return {
      summary,
      history: history?.snapshots ?? null,
      recommendations,
      ppiInputs:
        summary.ok && income && applied
          ? toPpiInputs(summary.data.metadata.baseDate, income, applied)
          : null,
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

/** 첫 계산은 비동기라 끝나도 화면이 모른다 — 이 간격으로 다시 조회한다. */
export const FIRST_CALCULATION_POLL_MS = 5_000;
/** 2분을 넘기면 멈추고 새로고침을 안내한다 — 계산이 막힌 채로 끝없이 조회하지 않는다. */
export const FIRST_CALCULATION_MAX_POLLS = 24;

export default function DashboardHome() {
  const loaderData = useLoaderData<typeof clientLoader>();
  const { summary } = loaderData;
  // 재검증 사이 loaderData 가 summary 만 가진 모양으로 올 수 있다(retention 테스트) — 없으면 없는 대로 그린다.
  const history = loaderData.history ?? null;
  const recommendations = loaderData.recommendations ?? null;
  const ppiInputs = loaderData.ppiInputs ?? null;
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

  const [pollCount, setPollCount] = useState(0);
  const pollExhausted = pollCount >= FIRST_CALCULATION_MAX_POLLS;
  useEffect(() => {
    if (!firstCalculation || pollExhausted) return;
    const timer = setTimeout(() => {
      setPollCount((count) => count + 1);
      revalidator.revalidate();
    }, FIRST_CALCULATION_POLL_MS);
    return () => clearTimeout(timer);
  }, [firstCalculation, pollExhausted, pollCount, revalidator]);

  return (
    <PageFrame>
      <PageHeader
        title="대시보드"
        description={formatVerdictDescription(data?.metadata.baseDate)}
        actions={
          <>
            <TextLink to="/onboarding/review">온보딩 검토 및 재시작</TextLink>
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
          </>
        }
      />

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
        <div className="mt-3">
          <InlineAlert
            message={toUserMessage(summary.error)}
            onRetry={() => revalidator.revalidate()}
            retrying={revalidator.state === "loading"}
          />
        </div>
      )}

      {data && firstCalculation && (
        <div
          role="status"
          className="mt-8 rounded-xl border border-dashed border-carix-border bg-carix-surface p-5 text-sm text-carix-text/70 shadow-xs"
        >
          <p>첫 지수를 계산하고 있습니다</p>
          <p className="mt-1 text-carix-text/70">
            {pollExhausted
              ? "계산이 늦어지고 있습니다. 잠시 뒤 새로고침해 주세요."
              : "보통 1~2분 걸립니다. 끝나면 이 화면에 바로 표시됩니다."}
          </p>
        </div>
      )}

      {data && !firstCalculation && (
        <div className="mt-6 flex flex-col gap-8">
          <VerdictHero
            signal={data.decisionSignal}
            history={history}
            recommendations={recommendations}
            negotiationTargets={data.negotiationTargets}
            wageBasis={data.metadata.wageStat?.basis}
          />

          <PageSection title="이 판정을 만든 지표">
            <div className="grid gap-4 md:grid-cols-3">
              <IndexCard
                title="구매력 지수"
                index={data.purchasingPowerIndex}
                baselineHint="기준 100: 물가와 동일한 소득 변화"
                helpContent={<PurchasingPowerHelp />}
                detailHref={trendsTabHref("income")}
                footer={
                  <>
                    <PriceDriverBadge driver={data.metadata.topPriceDriver} />
                    <PpiInputLine inputs={ppiInputs} />
                  </>
                }
              />
              <IndexCard
                title="시장가치 지수"
                index={data.marketValueIndex}
                baselineHint="기준 100: 이전 12개월 스킬 수요"
                helpContent={<MarketValueHelp />}
                detailHref="/dashboard/skills"
                footer={history && <MarketValueTrend history={history} />}
              />
              <IndexCard
                title="채용 활발도"
                index={data.openingWindowIndex}
                baselineHint="기준 100: 이전 12개월 동료집단 비중"
                helpContent={<OpeningWindowHelp />}
                footer={
                  <OpeningWindowBadges meta={data.metadata.openingWindow} />
                }
              />
            </div>
          </PageSection>

          <TrustPanel
            metadata={data.metadata}
            rationale={data.decisionSignal?.rationale ?? null}
            peerConditions={resolvePeerConditions(data.metadata.peerGroup)}
          />
        </div>
      )}
    </PageFrame>
  );
}

/**
 * 채용 활발도(OWI) 카드의 미니 뱃지 둘 (프런트엔드 설계 Card 4, b22).
 *
 * 전체 신규 수집 공고 중 동료집단 공고가 차지하는 최근 3개월 비중과 이전 12개월 비중을 표시한다.
 * 수집 이력 15개월 전이라 이전 창 표본이 부족하면 최근 비중 뱃지만 남는다.
 */
function OpeningWindowBadges({
  meta,
}: {
  meta: DashboardSummary["metadata"]["openingWindow"];
}) {
  if (
    !meta ||
    (meta.recentSharePercent === null && meta.baselineSharePercent === null)
  ) {
    return null;
  }

  return (
    <div className="flex flex-wrap gap-1.5">
      {meta.recentSharePercent !== null && (
        <Badge variant="secondary">
          최근 비중 {meta.recentSharePercent.toFixed(1)}%
        </Badge>
      )}
      {meta.baselineSharePercent !== null && (
        <Badge variant="secondary">
          이전 비중 {meta.baselineSharePercent.toFixed(1)}%
        </Badge>
      )}
    </div>
  );
}
