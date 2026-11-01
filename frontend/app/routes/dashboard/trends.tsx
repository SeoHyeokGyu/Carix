import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSection } from "@/components/layout/PageSection";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { InlineAlert } from "@/components/ui/inline-alert";
import { LoadingState } from "@/components/ui/loading-state";

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
import { TRENDS_SECTIONS } from "../../lib/trendsSections";

/** 앵커로 왔을 때 고정 헤더가 섹션 위를 덮지 않게 둔다(도움말 화면과 같은 값). */
const ANCHOR_OFFSET = "scroll-mt-24";

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
  const { hash } = useLocation();
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
  // 둘 다 실패하고 받아 둔 것도 없으면 섹션마다 실패를 그리지 않고 화면 단위 실패 하나.
  const screenFailed = !isLoading && Boolean(errorMessage) && !hasAnyData;

  // 섹션은 데이터가 온 뒤에 그려져 브라우저의 앵커 이동이 닿지 않는다 — 처음 그린 뒤 한 번 옮긴다.
  const anchored = useRef(false);
  useEffect(() => {
    if (isLoading || anchored.current || !hash) return;
    anchored.current = true;
    document.getElementById(hash.slice(1))?.scrollIntoView();
  }, [isLoading, hash]);

  return (
    <PageFrame>
      <PageHeader
        title="추이 분석"
        description="구매력·시장가치 지수 추이와 동료집단 안에서 내 연봉 위치를 함께 봅니다."
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
          {isLoading ? (
            <LoadingState message="시장 분석 데이터를 집계 및 분석하고 있습니다..." />
          ) : (
            <>
              {/* 받아 둔 데이터가 있으면 화면을 비우지 않고 위에 한 줄로 알린다. */}
              {errorMessage && (
                <InlineAlert
                  message={errorMessage}
                  onRetry={() => void loadData()}
                />
              )}
              {/* 섹션 제목은 차트 카드가 보인다 — 화면에는 sr-only h2 만 둔다. */}
              <PageSection
                titleHidden
                id={TRENDS_SECTIONS.income.id}
                className={ANCHOR_OFFSET}
                title={TRENDS_SECTIONS.income.title}
              >
                {trendData ? (
                  <IncomeVsPriceChart data={trendData} />
                ) : (
                  <SectionFailed
                    title="지수 추이 데이터를 불러오지 못했습니다"
                    onRetry={errorMessage ? null : () => void loadData()}
                  />
                )}
              </PageSection>
              <PageSection
                titleHidden
                id={TRENDS_SECTIONS.salary.id}
                className={ANCHOR_OFFSET}
                title={TRENDS_SECTIONS.salary.title}
              >
                {salaryData ? (
                  <SalaryDistributionChart data={salaryData} />
                ) : (
                  <SectionFailed
                    title="연봉 분포 데이터를 불러오지 못했습니다"
                    onRetry={errorMessage ? null : () => void loadData()}
                  />
                )}
              </PageSection>
            </>
          )}
        </>
      )}
    </PageFrame>
  );
}

function SectionFailed({
  title,
  onRetry,
}: {
  title: string;
  /** 위 알림이 이미 다시 시도를 가지면 null — 같은 버튼을 두 곳에 두지 않는다. */
  onRetry: (() => void) | null;
}) {
  return (
    <EmptyState variant="plain" tone="danger" icon="⚠️" title={title}>
      {onRetry && (
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          다시 시도
        </Button>
      )}
    </EmptyState>
  );
}
