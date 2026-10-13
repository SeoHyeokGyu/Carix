import { useEffect, useState } from "react";
import { TextLink } from "@/components/ui/text-link";
import { Button } from "@/components/ui/button";
import {
  fetchDataHealth,
  type DataHealthReport,
  type HealthCheck,
  type HealthVerdict,
} from "../../lib/collectorApi";
import { GAP_ANSWER_LABEL, GAP_REASON_LABEL } from "../../lib/dataHealth";

const BADGE: Record<HealthVerdict, { label: string; className: string }> = {
  OK: { label: "정상", className: "bg-carix-positive/15 text-carix-positive" },
  WARN: { label: "경고", className: "bg-carix-warning/15 text-carix-warning" },
  ERROR: { label: "오류", className: "bg-carix-danger/15 text-carix-danger" },
  UNKNOWN: {
    label: "판정 불가",
    className: "bg-carix-text/10 text-carix-text/60",
  },
};

const CHECKED_AT = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

function CheckRow({ check }: { check: HealthCheck }) {
  const badge = BADGE[check.verdict];
  return (
    <li className="flex flex-wrap items-center gap-2 text-xs">
      <span
        className={`rounded px-1.5 py-0.5 font-semibold ${badge.className}`}
      >
        {badge.label}
      </span>
      <span className="font-semibold">{check.title}</span>
      <span className="text-carix-text/70">{check.message}</span>
      {check.impactUsers != null && check.impactUsers > 0 && (
        <span className="text-carix-text/70">영향 {check.impactUsers}명</span>
      )}
      {check.drillDown && (
        <TextLink
          to={check.drillDown}
          aria-label={`${check.title} 근거`}
          direction="none"
          size="inherit"
        >
          근거
        </TextLink>
      )}
      {check.actionLink && check.action && (
        <TextLink
          to={check.actionLink}
          aria-label={`${check.title} ${check.action}`}
          direction="none"
          size="inherit"
        >
          {check.action}
        </TextLink>
      )}
    </li>
  );
}

/** S-20 맨 위 점검 줄. 실패해도 아래 탭은 그대로 쓴다. */
export function DataHealthStrip({
  onAuthError,
  refreshKey,
}: {
  onAuthError: () => void;
  refreshKey: number;
}) {
  const [attempt, setAttempt] = useState(0);
  const token = `${attempt}:${refreshKey}`;
  // settledToken 이 token 과 다르면 요청 중이다. effect 안에서 setState 를 동기 호출하지 않으려는 구조.
  const [state, setState] = useState<{
    settledToken: string | null;
    report: DataHealthReport | null;
    failed: boolean;
  }>({ settledToken: null, report: null, failed: false });

  useEffect(() => {
    let cancelled = false;
    fetchDataHealth()
      .then((report) => {
        if (!cancelled)
          setState({ settledToken: token, report, failed: false });
      })
      .catch((err) => {
        if (cancelled) return;
        if ((err as { status?: number }).status === 403) onAuthError();
        setState((prev) => ({ ...prev, settledToken: token, failed: true }));
      });
    return () => {
      cancelled = true;
    };
  }, [token, onAuthError]);

  const { report, failed } = state;
  const loading = state.settledToken !== token;

  const retry = (
    <Button
      variant="outline"
      size="sm"
      onClick={() => setAttempt((a) => a + 1)}
    >
      다시 점검
    </Button>
  );

  if (failed && !loading) {
    return (
      <section
        aria-label="점검"
        className="flex items-center gap-3 rounded border border-carix-border p-3 text-xs"
      >
        <span role="alert">점검을 불러오지 못했습니다</span>
        {retry}
      </section>
    );
  }
  if (!report) {
    return (
      <section aria-label="점검" className="text-xs text-carix-text/60">
        <span role="status">점검 중…</span>
      </section>
    );
  }

  const problems = report.checks.filter((c) => c.verdict !== "OK");
  const okCount = report.checks.length - problems.length;
  const { summary } = report;
  const gaps = summary?.gaps ?? [];
  return (
    <section
      aria-label="점검"
      className="space-y-2 rounded border border-carix-border p-3"
    >
      <div className="flex flex-wrap items-center gap-3 text-xs">
        <span className="font-semibold">
          {problems.length === 0
            ? "점검 정상"
            : `점검 ${problems.length}건 확인 필요`}
        </span>
        {summary ? (
          <span>
            답하는 사용자 {summary.fullyAnswered}/{summary.users}
          </span>
        ) : (
          <span className="text-carix-text/60">답하는 사용자 판정 불가</span>
        )}
        <span className="text-carix-text/60">
          기준 {report.referenceMonth} · 확인{" "}
          {CHECKED_AT.format(new Date(report.evaluatedAt))}
        </span>
        {retry}
        {loading && (
          <span role="status" className="text-carix-text/60">
            점검 중…
          </span>
        )}
      </div>
      {problems.length > 0 && (
        <ul className="space-y-1">
          {problems.map((c) => (
            <CheckRow key={c.id} check={c} />
          ))}
          {okCount > 0 && (
            <li className="text-xs text-carix-text/60">외 {okCount}개 정상</li>
          )}
        </ul>
      )}
      {gaps.length > 0 && (
        <p className="text-xs text-carix-text/70">
          빠진 사유:{" "}
          {gaps
            .map(
              (g) =>
                `${GAP_ANSWER_LABEL[g.answer] ?? g.answer} ${GAP_REASON_LABEL[g.reason] ?? g.reason} ${g.users}명`,
            )
            .join(" · ")}
        </p>
      )}
    </section>
  );
}
