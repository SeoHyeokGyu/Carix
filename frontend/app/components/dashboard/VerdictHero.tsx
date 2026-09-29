import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import type { Tone } from "@/components/ui/tone-badge";
import type { DecisionSignal } from "../../lib/api/dashboard";
import type { SnapshotPoint } from "../../lib/dashboardMarketApi";
import { resolveDecisionSignal } from "../../lib/decisionSignal";
import { countSignalStreak, toSparklineSeries } from "../../lib/verdict";
import { DecisionSignalHelp } from "./IndexHelp";
import { SignalBadge } from "./SignalBadge";
import { Sparkline } from "./Sparkline";

/** 점이 이보다 많으면 점은 여기서 멈추고 숫자가 나머지를 말한다. */
export const STREAK_DOT_LIMIT = 12;
const MIN_SPARKLINE_POINTS = 2;

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
  isNarrow: boolean;
}

/**
 * `S-10` 1층. 제목은 `decisionSignal.ts` 의 description 이다 — 새 문구를 만들지 않는다(설계 D2).
 * 서버 rationale 은 3층으로 내렸다(D3).
 */
export function VerdictHero({ signal, history, isNarrow }: VerdictHeroProps) {
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
          <h2
            id="verdict-title"
            className="font-display text-2xl font-bold leading-snug text-carix-text md:text-3xl"
          >
            {title}
          </h2>
          {signal && signal.rulesApplied.length > 0 && (
            <ul aria-label="적용 규칙" className="flex flex-wrap gap-1.5">
              {signal.rulesApplied.map((rule) => (
                <li key={rule}>
                  <Badge variant="secondary">{rule}</Badge>
                </li>
              ))}
            </ul>
          )}
        </div>
        {signal && history && (
          <VerdictPreview
            code={signal.code}
            tone={view.tone}
            history={history}
            isNarrow={isNarrow}
          />
        )}
      </Card>
    </section>
  );
}

function VerdictPreview({
  code,
  tone,
  history,
  isNarrow,
}: {
  code: string;
  tone: Tone;
  history: readonly SnapshotPoint[];
  isNarrow: boolean;
}) {
  const streak = countSignalStreak(history, code);
  const series = toSparklineSeries(history);
  const pointCount = series.segments.reduce((sum, s) => sum + s.length, 0);
  const showSparkline = !isNarrow && pointCount >= MIN_SPARKLINE_POINTS;
  if (streak === 0 && !showSparkline) return null;

  return (
    <aside className="flex shrink-0 flex-col gap-4 border-carix-border md:border-l md:pl-6">
      {streak > 0 && (
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
      )}
      {showSparkline && (
        <div className="flex flex-col gap-1.5">
          <p className="text-xs text-carix-text/60">
            시장가치 {pointCount}개월
          </p>
          <Sparkline series={series} />
        </div>
      )}
    </aside>
  );
}
