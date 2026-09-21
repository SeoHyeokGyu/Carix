import type { ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ToneBadge } from "@/components/ui/tone-badge";
import type { IndexValue } from "../../lib/api/dashboard";
import { resolveIndexStatus } from "../../lib/indexStatus";

function formatChange(change: number): string {
  const arrow = change > 0 ? "▲" : change < 0 ? "▼" : "－";
  return `${arrow} ${Math.abs(change).toFixed(1)}p`;
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
          {title}
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
                <Badge
                  variant={
                    changeFromLastMonth < 0 ? "destructive" : "secondary"
                  }
                >
                  {formatChange(changeFromLastMonth)}
                </Badge>
              )}
            </div>
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
