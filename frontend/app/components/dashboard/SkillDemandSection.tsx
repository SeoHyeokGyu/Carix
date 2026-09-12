import { useState } from "react";
import type {
  SkillDemandResponse,
  SkillDemandDetail,
} from "../../lib/dashboardMarketApi";

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
    // 상태값 우선 처리
    if (detail.status === "CUSTOM_SKILL_NO_STATS") {
      return (
        <span className="inline-flex items-center rounded bg-carix-warning/10 px-2 py-0.5 text-xs font-semibold text-carix-warning">
          통계 미제공
        </span>
      );
    }
    if (detail.status === "SAMPLE_TOO_SMALL") {
      return (
        <span className="inline-flex items-center rounded bg-carix-danger/10 px-2 py-0.5 text-xs font-semibold text-carix-danger">
          표본 부족
        </span>
      );
    }
    if (detail.status === "NEW_OBSERVATION") {
      return (
        <span className="inline-flex items-center rounded bg-carix-info/10 px-2 py-0.5 text-xs font-semibold text-carix-info">
          신규 관측
        </span>
      );
    }
    if (detail.status === "NO_RECENT_DEMAND") {
      return (
        <span className="inline-flex items-center rounded bg-carix-text/10 px-2 py-0.5 text-xs font-semibold text-carix-text/70">
          최근 수요 없음
        </span>
      );
    }

    // 지수 기준 추세 (기준 100)
    const idx = detail.demandIndex;
    if (idx >= 105) {
      return (
        <span className="inline-flex items-center gap-1 rounded bg-carix-positive/10 px-2 py-0.5 text-xs font-bold text-carix-positive">
          <span>▲ 상승</span>
          <span>({idx.toFixed(1)})</span>
        </span>
      );
    } else if (idx <= 95) {
      return (
        <span className="inline-flex items-center gap-1 rounded bg-carix-warning/10 px-2 py-0.5 text-xs font-bold text-carix-warning">
          <span>▼ 하락</span>
          <span>({idx.toFixed(1)})</span>
        </span>
      );
    } else {
      return (
        <span className="inline-flex items-center gap-1 rounded bg-carix-info/10 px-2 py-0.5 text-xs font-medium text-carix-info">
          <span>- 유지</span>
          <span>({idx.toFixed(1)})</span>
        </span>
      );
    }
  };

  return (
    <div
      data-testid="skill-demand-section"
      className="rounded-xl border border-carix-border bg-carix-surface p-6 shadow-sm"
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
          <span className="rounded-full bg-carix-info/10 border border-carix-info/30 px-3 py-1 text-xs font-medium text-carix-info">
            {coverage}
          </span>
        )}
      </div>

      {/* 2. 스킬 목록 테이블 */}
      {skills.length === 0 ? (
        <div className="py-12 text-center text-sm text-carix-text/60">
          <p>등록된 보유 스킬이 없습니다.</p>
          <p className="mt-1 text-xs text-carix-text/40">
            온보딩 또는 마이페이지에서 스킬을 등록하면 채용 시장 수요 지수가
            표시됩니다.
          </p>
        </div>
      ) : (
        <div className="mt-4 overflow-hidden rounded-lg border border-carix-border">
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
                                <span>• {src.source}</span>
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
          * 스킬 수요 지수는 최근 3개월 채용공고 언급 빈도율을 직전
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
