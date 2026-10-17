import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSection } from "@/components/layout/PageSection";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { InlineAlert } from "@/components/ui/inline-alert";
import { LoadingState } from "@/components/ui/loading-state";
import { TextLink } from "@/components/ui/text-link";

import { SkillDemandSection } from "../../components/dashboard/SkillDemandSection";
import { SkillRecommendationsSection } from "../../components/dashboard/SkillRecommendationsSection";
import { UnauthorizedError } from "../../lib/coreApi";
import {
  fetchSkillDemands,
  type SkillDemandResponse,
} from "../../lib/dashboardMarketApi";
import { LOGIN_PATH } from "../../lib/routeGuards";
import {
  fetchSkillRecommendations,
  type SkillRecommendationsResponse,
} from "../../lib/skillApi";

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
    { title: "스킬 분석 | Carix" },
    {
      name: "description",
      content: "보유 스킬의 시장 수요 추세와 동료집단 추천 스킬 분석",
    },
  ];
}

export default function DashboardSkills() {
  const navigate = useNavigate();

  const [skillDemandData, setSkillDemandData] =
    useState<SkillDemandResponse | null>(null);
  const [recommendationsData, setRecommendationsData] =
    useState<SkillRecommendationsResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const [demandRes, recommendRes] = await Promise.allSettled([
        fetchSkillDemands(),
        fetchSkillRecommendations(),
      ]);

      if (hasUnauthorized([demandRes, recommendRes])) {
        void navigate(LOGIN_PATH, { replace: true });
        return;
      }

      if (demandRes.status === "fulfilled") {
        setSkillDemandData(demandRes.value);
      }
      if (recommendRes.status === "fulfilled") {
        setRecommendationsData(recommendRes.value);
      }

      if (
        demandRes.status === "rejected" &&
        recommendRes.status === "rejected"
      ) {
        setErrorMessage("스킬 시장 데이터를 불러오지 못했습니다");
      }
    } finally {
      setIsLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadData();
  }, [loadData]);

  const hasAnyData = Boolean(skillDemandData || recommendationsData);
  const screenFailed = !isLoading && Boolean(errorMessage) && !hasAnyData;

  return (
    <PageFrame>
      <PageHeader
        title="스킬 분석"
        description="보유 스킬의 시장 수요 추세와 동료집단 공고에서 자주 요구하는 추천 스킬을 한곳에서 확인합니다."
        actions={
          <TextLink to="/mypage/records/skills">보유 스킬 관리</TextLink>
        }
        divider
      />

      {isLoading ? (
        <LoadingState message="스킬 시장 데이터를 집계 및 분석하고 있습니다..." />
      ) : screenFailed ? (
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
          {errorMessage && (
            <div className="mb-4">
              <InlineAlert
                message={errorMessage}
                onRetry={() => void loadData()}
              />
            </div>
          )}

          <PageSection
            title="보유 스킬 수요"
            description="시장가치 지수 산출에 반영된 보유 스킬별 가중치와 원천별 수요 추세입니다."
          >
            {skillDemandData ? (
              <SkillDemandSection data={skillDemandData} />
            ) : (
              <EmptyState
                variant="plain"
                tone="danger"
                icon="⚠️"
                title="보유 스킬 수요 데이터를 불러오지 못했습니다"
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
          </PageSection>

          <PageSection
            title="동료집단 추천 스킬"
            description="동료집단 채용공고에서 요구 빈도가 높은 미보유 스킬과 동시출현 근거입니다."
          >
            {recommendationsData ? (
              <SkillRecommendationsSection data={recommendationsData} />
            ) : (
              <EmptyState
                variant="plain"
                tone="danger"
                icon="⚠️"
                title="동료집단 추천 스킬 데이터를 불러오지 못했습니다"
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
          </PageSection>
        </>
      )}
    </PageFrame>
  );
}
