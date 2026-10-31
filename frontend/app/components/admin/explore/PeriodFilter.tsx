import { useState } from "react";

import { Input } from "@/components/ui/input";

import {
  fromMonthInput,
  periodError,
  toMonthInput,
} from "../../../lib/dataExplore";
import { FIELD } from "./exploreStyles";

/**
 * 월별 현황·CPI 의 기간 입력. URL 에 기간이 없으면 응답이 실제로 보여 준 범위를 채운다.
 * 기간이 맞지 않으면 URL 을 바꾸지 않고 메시지만 남긴다 — 입력칸은 URL 값으로 되돌아간다.
 */
export function PeriodFilter({
  from,
  to,
  shown,
  onApply,
}: {
  from?: string;
  to?: string;
  shown?: { from: string; to: string };
  onApply: (from: string | undefined, to: string | undefined) => void;
}) {
  const [message, setMessage] = useState<string | null>(null);

  const change = (nextFrom: string | undefined, nextTo: string | undefined) => {
    const error = periodError(nextFrom ?? shown?.from, nextTo ?? shown?.to);
    setMessage(error);
    if (error) return;
    onApply(nextFrom, nextTo);
  };

  return (
    <>
      <div role="group" aria-labelledby="period-label" className={FIELD}>
        <span id="period-label">기간</span>
        <span className="flex items-center gap-2 text-carix-text">
          <Input
            type="month"
            aria-label="시작 월"
            value={toMonthInput(from ?? shown?.from)}
            onChange={(e) =>
              change(fromMonthInput(e.target.value), to ?? shown?.to)
            }
            className="h-8 w-36 text-xs bg-carix-surface text-carix-text md:text-xs"
          />
          ~
          <Input
            type="month"
            aria-label="끝 월"
            value={toMonthInput(to ?? shown?.to)}
            onChange={(e) =>
              change(from ?? shown?.from, fromMonthInput(e.target.value))
            }
            className="h-8 w-36 text-xs bg-carix-surface text-carix-text md:text-xs"
          />
        </span>
      </div>
      {message && (
        <p role="alert" className="self-end pb-1.5 text-xs text-carix-danger">
          {message}
        </p>
      )}
    </>
  );
}
