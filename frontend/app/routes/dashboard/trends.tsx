import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSection } from "@/components/layout/PageSection";
import { useState, useEffect, useCallback } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Button } from "@/components/ui/button";
import {
  fetchSalaryDistribution,
  fetchSkillDemands,
  fetchTrends,
  type SalaryDistributionResponse,
  type SkillDemandResponse,
  type TrendListResponse,
} from "../../lib/dashboardMarketApi";
import { SalaryDistributionChart } from "../../components/dashboard/SalaryDistributionChart";
import { SkillDemandSection } from "../../components/dashboard/SkillDemandSection";
import { IncomeVsPriceChart } from "../../components/dashboard/IncomeVsPriceChart";
import { UnauthorizedError } from "../../lib/coreApi";
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
      content: "소득 대 개인물가, 스킬 수요, 동료집단 연봉 분포 추이 분석",
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
  const [skillData, setSkillData] = useState<SkillDemandResponse | null>(null);
  const [trendData, setTrendData] = useState<TrendListResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const [salaryRes, skillRes, trendRes] = await Promise.allSettled([
        fetchSalaryDistribution(),
        fetchSkillDemands(),
        fetchTrends(),
      ]);

      // 세션이 죽으면 셋이 동시에 401 이다. 부분 성공 렌더링과 갈라 둔다 — 한 원천만 죽은
      // 경우에 멀쩡한 화면을 로그인으로 내쫓으면 안 된다.
      if (hasUnauthorized([salaryRes, skillRes, trendRes])) {
        void navigate(LOGIN_PATH, { replace: true });
        return;
      }

      if (salaryRes.status === "fulfilled") {
        setSalaryData(salaryRes.value);
      }
      if (skillRes.status === "fulfilled") {
        setSkillData(skillRes.value);
      }
      if (trendRes.status === "fulfilled") {
        setTrendData(trendRes.value);
      }

      if (
        salaryRes.status === "rejected" &&
        skillRes.status === "rejected" &&
        trendRes.status === "rejected"
      ) {
        setErrorMessage("시장 분석 데이터를 불러오는 데 실패했습니다.");
      }
    } catch {
      setErrorMessage("데이터 조회 중 예기치 못한 오류가 발생했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadData();
  }, [loadData]);

  return (
    <PageFrame>
      <PageHeader
        title="추이 분석"
        description="소득 대 개인물가, 스킬 수요, 동료집단 연봉 분포를 다각도로 비교 분석합니다."
        divider
      />

      {/* 2. 탭 — 선택은 URL ?tab= 이 갖는다(대시보드 카드가 특정 탭으로 보낸다) */}
      <div
        role="tablist"
        aria-label="추이 분석 종류"
        className="mb-6 flex gap-2 border-b border-carix-border"
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
              className={`h-auto rounded-none px-4 py-2.5 text-sm border-b-2 ${
                selected
                  ? "border-carix-info text-carix-info font-bold"
                  : "border-transparent font-medium text-carix-text/60 hover:text-carix-text"
              }`}
            >
              {tab.label}
            </Button>
          );
        })}
      </div>

      {/* 3. 에러 알림 */}
      {errorMessage && (
        <div
          role="alert"
          className="mb-6 rounded-lg border border-carix-danger/30 bg-carix-danger/10 px-4 py-3 text-sm text-carix-danger flex items-center justify-between"
        >
          <span>{errorMessage}</span>
          <button
            type="button"
            onClick={() => void loadData()}
            className="text-xs font-bold underline"
          >
            다시 시도
          </button>
        </div>
      )}

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
            <div className="rounded-xl border border-carix-border bg-carix-surface p-12 text-center text-sm text-carix-text/60">
              시장 분석 데이터를 집계 및 분석하고 있습니다...
            </div>
          ) : (
            <div>
              {activeTab === "skills" && (
                <div>
                  {skillData ? (
                    <SkillDemandSection data={skillData} />
                  ) : (
                    <div className="rounded-xl border border-carix-border bg-carix-surface p-8 text-center text-sm text-carix-text/60">
                      스킬 수요 데이터를 불러오지 못했습니다.
                    </div>
                  )}
                </div>
              )}

              {activeTab === "salary" && (
                <div>
                  {salaryData ? (
                    <SalaryDistributionChart data={salaryData} />
                  ) : (
                    <div className="rounded-xl border border-carix-border bg-carix-surface p-8 text-center text-sm text-carix-text/60">
                      연봉 분포 데이터를 불러오지 못했습니다.
                    </div>
                  )}
                </div>
              )}

              {activeTab === "income" && (
                <div>
                  {trendData ? (
                    <IncomeVsPriceChart data={trendData} />
                  ) : (
                    <div className="rounded-xl border border-carix-border bg-carix-surface p-8 text-center text-sm text-carix-text/60">
                      소득 대 물가 추이 데이터를 불러오지 못했습니다.
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </PageSection>
      </div>
    </PageFrame>
  );
}
