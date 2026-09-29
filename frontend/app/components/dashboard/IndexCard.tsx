import type { ReactNode } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ToneBadge } from "@/components/ui/tone-badge";
import type { IndexValue } from "../../lib/api/dashboard";
import { resolveIndexStatus } from "../../lib/indexStatus";

function formatChange(change: number): string {
  const arrow = change > 0 ? "▲" : change < 0 ? "▼" : "－";
  return `${arrow} ${Math.abs(change).toFixed(1)}p`;
}

/** 눈금 한쪽 폭이 나타내는 지수 포인트. 50~150 이 막대 전체다. */
export const BASELINE_SPAN = 50;
const BASELINE = 100;

/** 기준 100 을 가운데(50%)에 두고 값까지의 막대. 범위를 넘으면 끝에 붙인다. */
export function toBaselineBar(value: number): { start: number; width: number } {
  const clamped = Math.min(
    Math.max(value, BASELINE - BASELINE_SPAN),
    BASELINE + BASELINE_SPAN,
  );
  const offset = ((clamped - BASELINE) / BASELINE_SPAN) * 50;
  return offset >= 0
    ? { start: 50, width: offset }
    : { start: 50 + offset, width: -offset };
}

function BaselineScale({ value }: { value: number }) {
  const { start, width } = toBaselineBar(value);
  return (
    <div
      aria-hidden="true"
      data-testid="baseline-scale"
      className="relative h-1.5 w-full rounded-full bg-carix-border"
    >
      <span className="absolute -top-1 -bottom-1 left-1/2 w-px bg-carix-text/60" />
      <span
        className="absolute inset-y-0 rounded-full bg-carix-info"
        style={{ left: `${start}%`, width: `${width}%` }}
      />
    </div>
  );
}

export function IndexCard({
  title,
  index,
  footer,
  helpContent,
  baselineHint,
}: {
  title: string;
  index: IndexValue;
  footer?: ReactNode;
  helpContent?: ReactNode;
  baselineHint?: string;
}) {
  const { value, changeFromLastMonth, indexStatus } = index;
  const status = resolveIndexStatus(indexStatus);

  return (
    <Card className="p-5 gap-3 transition-transform hover:-translate-y-0.5 duration-200">
      <CardHeader className="p-0 flex flex-row items-center justify-between gap-1">
        <CardTitle className="text-xs font-medium text-carix-text/70">
          <h4 className="text-xs font-medium">{title}</h4>
        </CardTitle>
        {helpContent}
      </CardHeader>
      <CardContent className="p-0 flex flex-col gap-2">
        {/* 값이 없으면(indexStatus !== "OK") 절대 0 이나 100 으로 채우지 않는다 — 아예 렌더링하지 않는다. */}
        {value !== null && (
          <div className="flex flex-col gap-1">
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-bold tracking-tight text-carix-text">
                {value.toFixed(1)}
              </span>
              {changeFromLastMonth !== null && (
                <ToneBadge tone={changeFromLastMonth < 0 ? "danger" : "muted"}>
                  {formatChange(changeFromLastMonth)}
                </ToneBadge>
              )}
            </div>
            <BaselineScale value={value} />
            {baselineHint && (
              <p className="text-[11px] text-carix-text/50">{baselineHint}</p>
            )}
          </div>
        )}
        {status && <ToneBadge tone={status.tone}>{status.label}</ToneBadge>}
        {footer}
      </CardContent>
    </Card>
  );
}
