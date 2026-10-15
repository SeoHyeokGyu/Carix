import { ChevronDown, ChevronUp, RotateCw } from "lucide-react";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { TextLink } from "@/components/ui/text-link";
import { type Tone, ToneBadge } from "@/components/ui/tone-badge";

import type { DecisionSignal } from "../../lib/api/dashboard";
import type { SnapshotPoint } from "../../lib/dashboardMarketApi";
import { resolveDecisionSignal } from "../../lib/decisionSignal";
import type { SkillRecommendationsResponse } from "../../lib/skillApi";
import { countSignalStreak } from "../../lib/verdict";
import { DecisionSignalHelp } from "./IndexHelp";
import { SignalBadge } from "./SignalBadge";

/** 점이 이보다 많으면 점은 여기서 멈추고 숫자가 나머지를 말한다. */
export const STREAK_DOT_LIMIT = 12;

/**
 * 톤 → 점 색. Tailwind 는 동적 클래스명을 못 보므로 정적 문자열 표로 둔다
 * ([UI_RULES] `eslint.config.js`, carix 토큰만 사용).
 */
const STREAK_DOT_TONE_CLASS: Record<Tone, string> = {
  positive: "bg-carix-positive",
  warning: "bg-carix-warning",
  danger: "bg-carix-danger",
  info: "bg-carix-info",
  action: "bg-carix-action",
  muted: "bg-carix-text/40",
};

interface VerdictHeroProps {
  signal: DecisionSignal | null;
  history: readonly SnapshotPoint[] | null;
  recommendations?: SkillRecommendationsResponse | null;
}

/**
 * `S-10` 1층. 제목은 `decisionSignal.ts` 의 description 이다 — 새 문구를 만들지 않는다(설계 D2).
 * 서버 rationale 은 3층으로 내렸다(D3).
 */
export function VerdictHero({
  signal,
  history,
  recommendations,
}: VerdictHeroProps) {
  const view = resolveDecisionSignal(signal?.code ?? "", signal?.label);
  const title = view.description ?? view.label;

  return (
    <section aria-labelledby="verdict-title">
      <Card className="gap-6 md:flex-row md:items-start md:justify-between">
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-2">
            {signal && (
              <SignalBadge code={signal.code} serverLabel={signal.label} />
            )}
            <DecisionSignalHelp />
          </div>
          <p
            id="verdict-title"
            data-testid="verdict-statement"
            className="font-display text-2xl font-bold leading-snug text-carix-text md:text-3xl"
          >
            {title}
          </p>
          {signal && signal.rulesApplied.length > 0 && (
            <ul aria-label="적용 규칙" className="flex flex-wrap gap-1.5">
              {signal.rulesApplied.map((rule) => (
                <li key={rule}>
                  <Badge variant="secondary">{rule}</Badge>
                </li>
              ))}
            </ul>
          )}
          {signal && (
            <SkillPrescriptionBlock
              signalCode={signal.code}
              recommendations={recommendations}
            />
          )}
        </div>
        {signal && history && (
          <VerdictPreview
            code={signal.code}
            tone={view.tone}
            history={history}
          />
        )}
      </Card>
    </section>
  );
}

/** 판정이 이어진 기간. 시장가치 추세는 판정의 근거라 2층 시장가치 카드가 갖는다. */
function VerdictPreview({
  code,
  tone,
  history,
}: {
  code: string;
  tone: Tone;
  history: readonly SnapshotPoint[];
}) {
  const streak = countSignalStreak(history, code);
  if (streak === 0) return null;

  return (
    <aside className="flex shrink-0 flex-col gap-4 border-carix-border md:border-l md:pl-6">
      <div className="flex flex-col gap-1.5">
        <p className="text-xs text-carix-text/60">이 판정이 이어진 기간</p>
        <div className="flex items-center gap-2">
          <span aria-hidden="true" className="flex gap-1">
            {Array.from(
              { length: Math.min(streak, STREAK_DOT_LIMIT) },
              (_, i) => (
                <span
                  key={i}
                  data-testid="streak-dot"
                  className={`size-2 rounded-full ${STREAK_DOT_TONE_CLASS[tone]}`}
                />
              ),
            )}
          </span>
          <span className="text-sm font-semibold text-carix-text">
            {streak}개월째
          </span>
        </div>
      </div>
      <TextLink to="/dashboard/history">지난 판정 보기</TextLink>
    </aside>
  );
}

interface PrescriptionHeaderConfig {
  badgeText: string;
  badgeTone: Tone;
  title: string;
}

function getPrescriptionHeader(
  signalCode: string | undefined,
): PrescriptionHeaderConfig {
  switch (signalCode) {
    case "UPSKILL":
      return {
        badgeText: "처방",
        badgeTone: "info",
        title: "시장가치 개선을 위한 우선 보강 스킬",
      };
    case "SEEK_JOB":
      return {
        badgeText: "이직 대비",
        badgeTone: "warning",
        title: "동료집단 공고에서 자주 요구하는 스킬",
      };
    case "NEGOTIATE":
      return {
        badgeText: "시장 수요",
        badgeTone: "action",
        title: "동료집단 공고에서 자주 요구하는 스킬",
      };
    case "STAY":
      return {
        badgeText: "성장 가이드",
        badgeTone: "positive",
        title: "동료집단 수요 기반 추천 스킬",
      };
    default:
      return {
        badgeText: "추천",
        badgeTone: "muted",
        title: "동료집단 수요 추천 스킬",
      };
  }
}

const SKILL_PAGE_SIZE = 3;

function SkillPrescriptionBlock({
  signalCode,
  recommendations,
}: {
  signalCode?: string;
  recommendations?: SkillRecommendationsResponse | null;
}) {
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [page, setPage] = useState(0);
  const items = recommendations?.recommendations ?? [];
  const isInsufficient = items.length === 0;
  const isUpskill = signalCode === "UPSKILL";

  // UPSKILL 이 아닌 신호(STAY, SEEK_JOB 등)에서는 추천 스킬이 비어있으면 블록 자체를 숨긴다 (불필요한 표본 부족 경고 노출 방지)
  if (!isUpskill && isInsufficient) {
    return null;
  }

  const { badgeText, badgeTone, title } = getPrescriptionHeader(signalCode);
  const totalPages = Math.ceil(items.length / SKILL_PAGE_SIZE);
  const currentItems = items.slice(
    page * SKILL_PAGE_SIZE,
    (page + 1) * SKILL_PAGE_SIZE,
  );

  const handleNextPage = () => {
    setPage((prev) => (prev + 1) % totalPages);
  };

  return (
    <div
      data-testid="skill-prescription-block"
      aria-label="스킬 보강 처방 및 추천"
      className="mt-1 flex flex-col gap-2.5 rounded-lg border border-carix-border bg-carix-surface/60 p-3.5"
    >
      <div className="flex items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <ToneBadge tone={badgeTone}>{badgeText}</ToneBadge>
          <span className="text-xs font-semibold text-carix-text/80">
            {title}
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          {isInsufficient && <ToneBadge tone="warning">표본 부족</ToneBadge>}
          {!isInsufficient && totalPages > 1 && (
            <Button
              type="button"
              variant="outline"
              size="xs"
              onClick={handleNextPage}
              aria-label={`다른 추천 보기 (${page + 1}/${totalPages})`}
              className="h-6 gap-1 px-2 text-[11px] font-normal text-carix-text/70 hover:text-carix-text"
            >
              <RotateCw className="size-3" />
              <span>
                다른 추천 ({page + 1}/{totalPages})
              </span>
            </Button>
          )}
          {!isInsufficient && (
            <Button
              type="button"
              variant="ghost"
              size="icon-xs"
              onClick={() => setIsCollapsed(!isCollapsed)}
              aria-label={isCollapsed ? "추천 스킬 펼치기" : "추천 스킬 접기"}
              className="text-carix-text/50 hover:text-carix-text"
            >
              {isCollapsed ? (
                <ChevronDown className="size-3.5" />
              ) : (
                <ChevronUp className="size-3.5" />
              )}
            </Button>
          )}
        </div>
      </div>

      {!isCollapsed && (
        <>
          {isInsufficient ? (
            <p className="text-xs text-carix-text/60">
              동료집단 공고 표본이 부족하여 추천 스킬을 산출할 수 없습니다.
            </p>
          ) : (
            <ul aria-label="추천 스킬 목록" className="flex flex-col gap-2">
              {currentItems.map((item) => (
                <li
                  key={item.standardName}
                  className="flex flex-col gap-1 rounded border border-carix-border/60 bg-carix-surface/80 p-2.5"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-semibold text-carix-text">
                      {item.standardName}
                    </span>
                    {item.demandRate !== null && (
                      <Badge variant="secondary">
                        수요율 {(item.demandRate * 100).toFixed(1)}%
                      </Badge>
                    )}
                    <GrowthTrendBadge trend={item.growthTrend} />
                  </div>
                  {item.reason && (
                    <p className="text-xs text-carix-text/70">{item.reason}</p>
                  )}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}

function GrowthTrendBadge({ trend }: { trend?: string | null }) {
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
