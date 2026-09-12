import { useState } from "react";
import { Link, redirect, useLoaderData, useRevalidator } from "react-router";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { IndexCard } from "../../components/dashboard/IndexCard";
import { toUserMessage } from "../../lib/apiError";
import { fetchSummary, type DashboardSummary } from "../../lib/api/dashboard";
import { UnauthorizedError } from "../../lib/coreApi";
import { LOGIN_PATH } from "../../lib/routeGuards";

export async function clientLoader() {
  try {
    return { summary: await fetchSummary() };
  } catch (error) {
    // fetchSummary 는 UnauthorizedError 만 다시 던진다(설계 §10) — 다른 화면과 같은 판단으로
    // 로그인으로 보낸다. 그 밖의 경로는 애초에 fetchSummary 가 던지지 않으므로 여기 오지 않는다.
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    throw error;
  }
}

/**
 * 「첫 계산 전」 판정 (설계 §7.5) — PPI 와 MVI 가 **둘 다** 값이 없고 `NOT_AVAILABLE` 이어야
 * 한다. "값이 둘 다 없다"만으로 근사하면 한쪽이 표본 부족 등 다른 열화일 때도 이 상태로
 * 오판한다 — 그래서 indexStatus 조건을 값 조건과 함께 명시적으로 본다.
 */
function isFirstCalculation(data: DashboardSummary): boolean {
  return (
    data.purchasingPowerIndex.value === null &&
    data.purchasingPowerIndex.indexStatus === "NOT_AVAILABLE" &&
    data.marketValueIndex.value === null &&
    data.marketValueIndex.indexStatus === "NOT_AVAILABLE"
  );
}

export default function DashboardHome() {
  const { summary } = useLoaderData<typeof clientLoader>();
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
  const [prevSummary, setPrevSummary] = useState(summary);
  if (summary !== prevSummary) {
    setPrevSummary(summary);
    if (summary.ok) setLastGood(summary.data);
  }

  const [isRationaleOpen, setIsRationaleOpen] = useState(false);

  const data = summary.ok ? summary.data : lastGood;
  const firstCalculation = data !== null && isFirstCalculation(data);

  return (
    <section className="mx-auto w-full max-w-4xl px-6 py-10">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-carix-text/70">
          기준일: {data?.metadata.baseDate ?? "-"}
        </p>
        <div className="flex flex-wrap items-center gap-4">
          <Link
            to="/dashboard/history"
            className="text-sm text-carix-info underline"
          >
            월별 스냅샷 이력 보기
          </Link>
          <Link
            to="/onboarding/review"
            className="text-sm text-carix-text/70 hover:text-carix-text underline"
          >
            온보딩 검토 및 재시작
          </Link>
        </div>
      </div>

      <div className="mt-6 flex items-center justify-between gap-3">
        <h2 className="font-display text-2xl font-bold text-carix-text">
          핵심 지표
        </h2>
      </div>

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
        <div className="mt-8 rounded-lg border border-dashed border-carix-border bg-carix-surface px-5 py-4 text-sm text-carix-text/70">
          <p>첫 지수를 계산하고 있습니다</p>
          <p className="mt-1 text-carix-text/50">
            계산이 끝나면 이 화면에 자동으로 표시됩니다. 잠시 후 다시 확인해
            주세요.
          </p>
        </div>
      )}

      {data && !firstCalculation && (
        <div className="mt-6 grid gap-4 sm:grid-cols-3">
          <IndexCard title="구매력 지수" index={data.purchasingPowerIndex} />
          <IndexCard title="시장가치 지수" index={data.marketValueIndex} />

          {data.decisionSignal && (
            <Card>
              <CardHeader>
                <CardTitle>결정 신호</CardTitle>
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                <Badge>{data.decisionSignal.label}</Badge>

                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  aria-expanded={isRationaleOpen}
                  onClick={() => setIsRationaleOpen((open) => !open)}
                  className="self-start"
                >
                  근거 보기
                </Button>

                {isRationaleOpen && (
                  <div className="flex flex-col gap-2 text-sm text-carix-text/80">
                    <p>{data.decisionSignal.rationale}</p>
                    <ul className="flex flex-col gap-1">
                      {data.decisionSignal.rulesApplied.map((rule) => (
                        <li key={rule}>{rule}</li>
                      ))}
                    </ul>
                    {data.metadata.peerGroup && (
                      <p>
                        적용 비교 조건:{" "}
                        <span>{data.metadata.peerGroup.appliedConditions}</span>
                      </p>
                    )}
                    {data.metadata.ruleVersion && (
                      <p>
                        규칙 버전: <span>{data.metadata.ruleVersion}</span>
                      </p>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </section>
  );
}
