import { Badge } from "@/components/ui/badge";
import { Card, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ToneBadge } from "@/components/ui/tone-badge";

import type {
  SkillRecommendationItem,
  SkillRecommendationsResponse,
} from "../../lib/skillApi";
import { formatAppliedConditions } from "../../lib/sourceLabel";

export interface SkillRecommendationsSectionProps {
  data: SkillRecommendationsResponse;
}

function GrowthTrendBadge({
  trend,
}: {
  trend?: SkillRecommendationItem["growthTrend"] | null;
}) {
  if (!trend) return null;
  if (trend === "UP") {
    return <ToneBadge tone="positive">▲ 상승</ToneBadge>;
  }
  if (trend === "DOWN") {
    return <ToneBadge tone="warning">▼ 하락</ToneBadge>;
  }
  if (trend === "STABLE") {
    return <ToneBadge tone="muted">- 유지</ToneBadge>;
  }
  return null;
}

export function SkillRecommendationsSection({
  data,
}: SkillRecommendationsSectionProps) {
  const { peerGroup, recommendations } = data;
  const rawConditions = peerGroup?.appliedConditions?.trim() ?? "";
  const appliedConditions = rawConditions
    ? formatAppliedConditions(rawConditions)
    : "";

  return (
    <Card data-testid="skill-recommendations-section">
      {/* 1. 섹션 헤더 및 동료집단 비교 조건 */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-carix-border pb-4">
        <div>
          <CardTitle>동료집단 수요 기반 추천 스킬</CardTitle>
          <p className="mt-1 text-xs text-carix-text/70">
            동료집단 채용공고에서 자주 요구되는 미보유 스킬과 보유 스킬 동시출현
            근거
          </p>
        </div>
        {appliedConditions && (
          <Badge variant="secondary">비교 기준: {appliedConditions}</Badge>
        )}
      </div>

      {/* 2. 추천 스킬 목록 */}
      {recommendations.length === 0 ? (
        <EmptyState
          variant="plain"
          icon="📊"
          title="동료집단 공고 표본이 부족합니다."
          description="동료집단 공고 표본이 부족하여 추천 스킬을 산출할 수 없습니다."
        />
      ) : (
        <ul
          aria-label="동료집단 추천 스킬 목록"
          // 한 줄이 넓으면 스킬명과 수요율 배지 사이가 멀어져 행을 따라 읽기 어렵다 — 넓은 화면은 2열.
          className="grid gap-3 lg:grid-cols-2"
        >
          {recommendations.map((item) => (
            <li
              key={item.standardName}
              className="flex flex-col gap-1.5 rounded-lg border border-carix-border/80 bg-carix-bg/40 p-3.5"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-semibold text-carix-text">
                    {item.standardName}
                  </span>
                  {item.category && (
                    <Badge variant="outline">{item.category}</Badge>
                  )}
                </div>
                <div className="flex flex-wrap items-center gap-1.5">
                  {item.demandRate !== null && (
                    <Badge variant="secondary">
                      수요율 {(item.demandRate * 100).toFixed(1)}%
                    </Badge>
                  )}
                  <GrowthTrendBadge trend={item.growthTrend} />
                </div>
              </div>
              {item.reason && (
                <p className="text-xs text-carix-text/70">{item.reason}</p>
              )}
            </li>
          ))}
        </ul>
      )}

      {/* 3. 하단 안내 각주 */}
      <div className="mt-1 space-y-1 text-[11px] text-carix-text/60">
        <p>
          * 동료집단 채용공고에서 요구 빈도가 높은 스킬 중 현재 보유하지 않은
          스킬을 수요율 순으로 제공합니다.
        </p>
        <p>
          * 사용자 정의 스킬은 시장 집계 매칭 대상이 아니므로 추천 후보 및
          동시출현 기준 스킬에서 제외됩니다.
        </p>
      </div>
    </Card>
  );
}
