import type { DataHealthReport } from "../../lib/collectorApi";
import { GAP_REASON_LABEL, VERDICT_LABEL } from "../../lib/dataHealth";

export function DataHealthSummary({ report }: { report: DataHealthReport }) {
  const { summary } = report;
  return (
    <section
      aria-label="요약"
      className="rounded-xl border border-carix-border bg-carix-surface p-5 space-y-3"
    >
      <div className="flex flex-wrap items-baseline gap-3">
        <h2 className="text-sm text-carix-text/70">
          세 질문에 온전히 답하는 사용자
        </h2>
        <p className="text-2xl font-bold">
          {summary.fullyAnswered} / {summary.users}명
        </p>
      </div>
      {/* 점검 줄(listitem)과 섞이지 않게 목록 요소를 쓰지 않는다 */}
      <div className="flex flex-wrap gap-2 text-xs">
        {(["ERROR", "WARN", "UNKNOWN"] as const).map((v) => (
          <span
            key={v}
            className="rounded-md border border-carix-border px-2 py-1"
          >
            {VERDICT_LABEL[v]} {summary.verdictCounts[v] ?? 0}
          </span>
        ))}
      </div>
      {summary.gaps.length > 0 && (
        <p className="text-xs text-carix-text/70">
          빈 답:{" "}
          {summary.gaps
            .map(
              (g) =>
                `${g.answer} ${GAP_REASON_LABEL[g.reason] ?? g.reason} ${g.users}명`,
            )
            .join(" · ")}
        </p>
      )}
      <p className="text-[11px] text-carix-text/50">
        평가 {new Date(report.evaluatedAt).toLocaleString("ko-KR")} · 기준월{" "}
        {report.referenceMonth} · 엔진 규칙{" "}
        {report.engineRuleVersion ?? "확인 불가"}
      </p>
    </section>
  );
}
