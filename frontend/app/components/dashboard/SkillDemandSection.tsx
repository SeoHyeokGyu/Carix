import { useState } from "react";
import type {
  SkillDemandResponse,
  SkillDemandDetail,
} from "../../lib/dashboardMarketApi";
import {
  resolveDemandTrend,
  resolveSkillDemandStatus,
} from "../../lib/skillDemandStatus";
import { sourceLabel } from "../../lib/sourceLabel";
import { ToneBadge } from "@/components/ui/tone-badge";
import { EmptyState } from "@/components/ui/empty-state";

export interface SkillDemandSectionProps {
  data: SkillDemandResponse;
}

export function SkillDemandSection({ data }: SkillDemandSectionProps) {
  const [expandedSkillId, setExpandedSkillId] = useState<string | null>(null);

  const { skills, coverage } = data;

  const toggleExpand = (skillId: string) => {
    setExpandedSkillId((prev) => (prev === skillId ? null : skillId));
  };

  const getTrendBadge = (detail: SkillDemandDetail) => {
    const status = resolveSkillDemandStatus(detail.status);
    if (status) {
      return <ToneBadge tone={status.tone}>{status.label}</ToneBadge>;
    }

    const trend = resolveDemandTrend(detail.demandIndex);
    return (
      <ToneBadge tone={trend.tone}>
        <span>{trend.label}</span>
        <span>({detail.demandIndex.toFixed(1)})</span>
      </ToneBadge>
    );
  };

  return (
    <div
      data-testid="skill-demand-section"
      className="rounded-xl border border-carix-border bg-carix-surface p-6 shadow-xs"
    >
      {/* 1. 섹션 헤더 및 커버리지 정보 */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-carix-border pb-4">
        <div>
          <h3 className="font-display text-lg font-bold text-carix-text">
            보유 스킬 수요 추세
          </h3>
          <p className="mt-1 text-xs text-carix-text/70">
            최근 3개월 채용공고 수요율 대비 직전 12개월(4~15개월 전) 기준선(100)
            비교 지수
          </p>
        </div>
        {coverage && (
          <ToneBadge tone="info" className="rounded-full px-3 py-1 font-medium">
            {coverage}
          </ToneBadge>
        )}
      </div>

      {/* 2. 스킬 목록 테이블 */}
      {skills.length === 0 ? (
        <EmptyState
          variant="plain"
          title="등록된 보유 스킬이 없습니다."
          description="온보딩 또는 마이페이지에서 스킬을 등록하면 채용 시장 수요 지수가 표시됩니다."
        />
      ) : (
        <div className="mt-4 overflow-hidden rounded-xl border border-carix-border bg-carix-surface shadow-xs">
          <table className="w-full text-left text-sm text-carix-text">
            <thead className="border-b border-carix-border bg-carix-bg/60 text-xs font-medium text-carix-text/70">
              <tr>
                <th scope="col" className="px-4 py-3">
                  스킬명
                </th>
                <th scope="col" className="px-4 py-3 text-center">
                  가중치
                </th>
                <th scope="col" className="px-4 py-3 text-center">
                  최근 3개월 수요 추세
                </th>
                <th scope="col" className="px-4 py-3 text-right">
                  원천별 상세
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-carix-border">
              {skills.map((skill) => {
                const isExpanded = expandedSkillId === skill.skillId;
                const isClamped =
                  skill.demandIndex === 25 || skill.demandIndex === 400;

                return (
                  <tr
                    key={skill.skillId}
                    className="group hover:bg-carix-bg/40"
                  >
                    <td className="px-4 py-3.5">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold">
                          {skill.standardName}
                        </span>
                        {isClamped && (
                          <span
                            className="rounded bg-carix-text/10 px-1.5 py-0.5 text-[10px] text-carix-text/70"
                            title="극단값 방지를 위해 지수가 [25, 400] 범위로 제한되었습니다."
                          >
                            이상치 보정
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs">
                      {skill.status === "CUSTOM_SKILL_NO_STATS" ? (
                        <span className="text-carix-text/40">-</span>
                      ) : (
                        <span className="font-mono font-medium">
                          {(skill.weight * 100).toFixed(1)}%
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3.5 text-center">
                      {getTrendBadge(skill)}
                    </td>
                    <td className="px-4 py-3.5 text-right">
                      {skill.sources && skill.sources.length > 0 ? (
                        <button
                          type="button"
                          onClick={() => toggleExpand(skill.skillId)}
                          className="text-xs font-medium text-carix-info hover:underline"
                        >
                          {isExpanded ? "접기 ▲" : "원천 통계 보기 ▼"}
                        </button>
                      ) : (
                        <span className="text-xs text-carix-text/40">-</span>
                      )}

                      {/* 원천별 상세 아코디언 */}
                      {isExpanded && skill.sources && (
                        <div className="mt-2 rounded-lg bg-carix-bg p-3 text-left border border-carix-border/80">
                          <p className="text-[11px] font-semibold text-carix-text/80 mb-2">
                            수집 원천별 표본 및 지수:
                          </p>
                          <div className="space-y-1 text-xs">
                            {skill.sources.map((src) => (
                              <div
                                key={src.source}
                                className="flex justify-between items-center text-carix-text/80"
                              >
                                <span>• {sourceLabel(src.source)}</span>
                                <span>
                                  표본: <strong>{src.sampleSize}건</strong> |
                                  지수:{" "}
                                  <strong>{src.demandIndex.toFixed(1)}</strong>
                                </span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* 3. 하단 안내 각주 */}
      <div className="mt-5 text-[11px] text-carix-text/60 space-y-1">
        <p>
          * 스킬 수요 지수는 최근 3개월 신규 채용공고(유량) 언급 빈도율을 직전
          12개월(4~15개월 전 비중첩)과 비교한 값입니다 (100 = 전년 동등 수요
          유지).
        </p>
        <p>
          * 사전에 없는 사용자 정의 스킬은 공고 매칭이 불가능하여 시장가치 지수
          가중합에서 제외되고, 나머지 표준 스킬의 가중치 합이 100%로
          재정규화됩니다.
        </p>
      </div>
    </div>
  );
}
