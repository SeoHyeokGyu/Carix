import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import type { CpiTable } from "../../../lib/collectorApi";
import { CPI_CATEGORY_NAMES, isSharpCpiChange } from "../../../lib/dataExplore";
import { CELL, HEAD, META, NUM } from "./exploreStyles";

const MISSING_CELL = "bg-carix-warning/10 text-carix-warning";
// 급변은 원인 미확인이라 오류가 아니다 — 경고색 대신 중립 배경.
const SHARP_CELL = "bg-carix-info/10";
const SHARP_TITLE = "값 확인 — 전월 대비 5% 넘게 변함";

/** CPI 월 × 품목 표. 결측 칸은 경고색, 전월 대비 급변 칸은 중립 배경과 「!」. */
export function CpiView({ data }: { data: CpiTable }) {
  if (data.months.length === 0) {
    return (
      <p className="text-xs text-carix-text/60">
        해당 조건의 데이터가 없습니다
      </p>
    );
  }
  const ascending = [...data.months].sort((a, b) =>
    a.month.localeCompare(b.month),
  );
  const prevOf = new Map(
    ascending.map((m, i) => [m.month, ascending[i - 1]] as const),
  );
  return (
    <div className="space-y-2">
      <p className={META}>
        <span>KOSIS 소비자물가 지출목적별 지수 (2020=100)</span>
        <span>
          <span className="rounded bg-carix-info/10 px-1 font-semibold">!</span>{" "}
          전월 대비 5% 넘게 변함
        </span>
        <span>
          <span className="rounded bg-carix-warning/10 px-1 text-carix-warning">
            —
          </span>{" "}
          값 없음
        </span>
      </p>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className={HEAD}>월</TableHead>
            {data.codes.map((c) => (
              <TableHead key={c} className={`${HEAD} ${NUM}`}>
                {CPI_CATEGORY_NAMES[c] ? `${c} ${CPI_CATEGORY_NAMES[c]}` : c}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.months.map((m) => (
            <TableRow key={m.month}>
              <TableCell className={`${CELL} tabular-nums`}>
                {m.month}
              </TableCell>
              {data.codes.map((c) => {
                const v = m.values[c];
                const sharp = isSharpCpiChange(
                  prevOf.get(m.month)?.values[c],
                  v,
                );
                return (
                  <TableCell
                    key={c}
                    data-testid={`cpi-${m.month}-${c}`}
                    data-missing={String(v === undefined)}
                    data-sharp={String(sharp)}
                    title={sharp ? SHARP_TITLE : undefined}
                    className={`${CELL} ${NUM} ${v === undefined ? MISSING_CELL : sharp ? SHARP_CELL : ""}`}
                  >
                    {v === undefined ? "—" : v.toFixed(2)}
                    {sharp && (
                      <>
                        <sup
                          aria-hidden="true"
                          className="ml-0.5 font-semibold"
                        >
                          !
                        </sup>
                        <span className="sr-only">{SHARP_TITLE}</span>
                      </>
                    )}
                  </TableCell>
                );
              })}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
