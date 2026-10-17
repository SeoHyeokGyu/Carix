import { useCallback, useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSection } from "@/components/layout/PageSection";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { InlineAlert } from "@/components/ui/inline-alert";
import { LoadingState } from "@/components/ui/loading-state";
import {
  TAB_ACTIVE,
  TAB_IDLE,
  TAB_ITEM,
  TAB_LIST,
} from "@/components/ui/tab-styles";
import { cn } from "@/lib/utils";

import { IncomeVsPriceChart } from "../../components/dashboard/IncomeVsPriceChart";
import { SalaryDistributionChart } from "../../components/dashboard/SalaryDistributionChart";
import { UnauthorizedError } from "../../lib/coreApi";
import {
  fetchSalaryDistribution,
  fetchTrends,
  type SalaryDistributionResponse,
  type TrendListResponse,
} from "../../lib/dashboardMarketApi";
import { LOGIN_PATH } from "../../lib/routeGuards";
import {
  resolveTrendsTab,
  TRENDS_TABS,
  type TrendsTabKey,
} from "../../lib/trendsTabs";

/** 거절된 것 중 하나라도 세션 만료면 참. 순수 함수다. */
function hasUnauthorized(results: PromiseSettledResult<unknown>[]): boolean {
  return results.some(
    (result) =>
      result.status === "rejected" &&
      result.reason instanceof UnauthorizedError,
  );
}

export function meta() {
  return [
    { title: "추이 분석 | Carix" },
    {
      name: "description",
      content: "소득 대 개인물가, 동료집단 연봉 분포 추이 분석",
    },
  ];
}

export default function DashboardTrends() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = resolveTrendsTab(searchParams.get("tab"));
  const selectTab = (tab: TrendsTabKey) =>
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set("tab", tab);
        return next;
      },
      { replace: true },
    );

  const navigate = useNavigate();

  // 데이터 상태
  const [salaryData, setSalaryData] =
    useState<SalaryDistributionResponse | null>(null);
  const [trendData, setTrendData] = useState<TrendListResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const [salaryRes, trendRes] = await Promise.allSettled([
        fetchSalaryDistribution(),
        fetchTrends(),
      ]);

      // 세션이 죽으면 둘이 동시에 401 이다. 부분 성공 렌더링과 갈라 둔다 — 한 원천만 죽은
      // 경우에 멀쩡한 화면을 로그인으로 내쫓으면 안 된다.
      if (hasUnauthorized([salaryRes, trendRes])) {
        void navigate(LOGIN_PATH, { replace: true });
        return;
      }

      if (salaryRes.status === "fulfilled") {
        setSalaryData(salaryRes.value);
      }
      if (trendRes.status === "fulfilled") {
        setTrendData(trendRes.value);
      }

      if (salaryRes.status === "rejected" && trendRes.status === "rejected") {
        setErrorMessage("시장 분석 데이터를 불러오지 못했습니다");
      }
    } finally {
      setIsLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadData();
  }, [loadData]);

  const hasAnyData = Boolean(salaryData || trendData);
  // 둘 다 실패하고 받아 둔 것도 없으면 고를 탭이 없다 — 탭 대신 화면 단위 실패 하나.
  const screenFailed = !isLoading && Boolean(errorMessage) && !hasAnyData;

  return (
    <PageFrame>
      <PageHeader
        title="추이 분석"
        description="소득 대 개인물가, 동료집단 연봉 분포를 다각도로 비교 분석합니다."
      />

      {screenFailed ? (
        <EmptyState
          tone="danger"
          icon="⚠️"
          title={errorMessage ?? ""}
          description="잠시 후 다시 시도해 주세요."
        >
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void loadData()}
          >
            다시 시도
          </Button>
        </EmptyState>
      ) : (
        <>
          {/* 2. 탭 — 선택은 URL ?tab= 이 갖는다(대시보드 카드가 특정 탭으로 보낸다) */}
          <div
            role="tablist"
            aria-label="추이 분석 종류"
            className={cn("mb-6", TAB_LIST)}
          >
            {TRENDS_TABS.map((tab) => {
              const selected = activeTab === tab.key;
              return (
                <Button
                  key={tab.key}
                  type="button"
                  variant="ghost"
                  role="tab"
                  id={`trends-tab-${tab.key}`}
                  aria-selected={selected}
                  aria-controls="trends-panel"
                  onClick={() => selectTab(tab.key)}
                  className={cn(
                    "h-auto rounded-none",
                    TAB_ITEM,
                    selected ? TAB_ACTIVE : TAB_IDLE,
                  )}
                >
                  {tab.label}
                </Button>
              );
            })}
          </div>

          {/* 4. 탭 콘텐츠 영역 */}
          <div
            role="tabpanel"
            id="trends-panel"
            aria-labelledby={`trends-tab-${activeTab}`}
          >
            <PageSection
              titleHidden
              title={TRENDS_TABS.find((t) => t.key === activeTab)?.label ?? ""}
            >
              {isLoading ? (
                <LoadingState message="시장 분석 데이터를 집계 및 분석하고 있습니다..." />
              ) : (
                <div>
                  {/* 받아 둔 데이터가 있으면 화면을 비우지 않고 위에 한 줄로 알린다. */}
                  {errorMessage && (
                    <div className="mb-4">
                      <InlineAlert
                        message={errorMessage}
                        onRetry={() => void loadData()}
                      />
                    </div>
                  )}
                  {activeTab === "salary" && (
                    <div>
                      {salaryData ? (
                        <SalaryDistributionChart data={salaryData} />
                      ) : (
                        <EmptyState
                          variant="plain"
                          tone="danger"
                          icon="⚠️"
                          title="연봉 분포 데이터를 불러오지 못했습니다"
                        >
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => void loadData()}
                          >
                            다시 시도
                          </Button>
                        </EmptyState>
                      )}
                    </div>
                  )}

                  {activeTab === "income" && (
                    <div>
                      {trendData ? (
                        <IncomeVsPriceChart data={trendData} />
                      ) : (
                        <EmptyState
                          variant="plain"
                          tone="danger"
                          icon="⚠️"
                          title="소득 대 물가 추이 데이터를 불러오지 못했습니다"
                        >
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => void loadData()}
                          >
                            다시 시도
                          </Button>
                        </EmptyState>
                      )}
                    </div>
                  )}
                </div>
              )}
            </PageSection>
          </div>
        </>
      )}
    </PageFrame>
  );
}
