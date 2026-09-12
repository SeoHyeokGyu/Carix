import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { IndexStatus, IndexValue } from "../../lib/api/dashboard";

/**
 * `OK` 가 아닐 때 카드에 보여줄 뱃지 문구 (설계 §7.5 표).
 * `INSUFFICIENT_HISTORY`·`NOT_AVAILABLE` 은 화면 구분 없이 같은 "계산 대기"로 묶인다.
 */
const STATUS_BADGE_LABEL: Record<Exclude<IndexStatus, "OK">, string> = {
  INSUFFICIENT_SAMPLE: "표본 부족",
  UPDATE_DELAYED: "업데이트 지연",
  INSUFFICIENT_HISTORY: "계산 대기",
  NOT_AVAILABLE: "계산 대기",
};

function formatChange(change: number): string {
  const arrow = change > 0 ? "▲" : change < 0 ? "▼" : "－";
  return `${arrow} ${Math.abs(change).toFixed(1)}p`;
}

export function IndexCard({
  title,
  index,
}: {
  title: string;
  index: IndexValue;
}) {
  const { value, changeFromLastMonth, indexStatus } = index;

  return (
    <Card className="p-5 gap-3 transition-transform hover:-translate-y-0.5 duration-200">
      <CardHeader className="p-0">
        <CardTitle className="text-xs font-medium text-carix-text/70">
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0 flex flex-col gap-2">
        {/* 값이 없으면(indexStatus !== "OK") 절대 0 이나 100 으로 채우지 않는다 — 아예 렌더링하지 않는다. */}
        {value !== null && (
          <div className="flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight text-carix-text">
              {value.toFixed(1)}
            </span>
            {changeFromLastMonth !== null && (
              <Badge
                variant={changeFromLastMonth < 0 ? "destructive" : "secondary"}
              >
                {formatChange(changeFromLastMonth)}
              </Badge>
            )}
          </div>
        )}
        {indexStatus !== "OK" && (
          <Badge variant="outline">{STATUS_BADGE_LABEL[indexStatus]}</Badge>
        )}
      </CardContent>
    </Card>
  );
}
