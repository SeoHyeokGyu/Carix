import { useState } from "react";
import { Link } from "react-router";
import type { HealthCheck, HealthGroup } from "../../lib/collectorApi";
import { sortChecks, VERDICT_LABEL } from "../../lib/dataHealth";
import { Button } from "../ui/button";

const ICON: Record<HealthCheck["verdict"], string> = {
  OK: "✓",
  WARN: "!",
  ERROR: "✕",
  UNKNOWN: "?",
};

function Row({ check }: { check: HealthCheck }) {
  const [open, setOpen] = useState(false);
  return (
    <li className="py-2 border-b border-carix-border last:border-0 text-xs">
      <div className="flex flex-wrap items-center gap-2">
        <span aria-hidden className="w-4 text-center font-bold">
          {ICON[check.verdict]}
        </span>
        <span className="font-semibold">{check.title}</span>
        <span className="rounded px-1.5 py-0.5 border border-carix-border">
          {VERDICT_LABEL[check.verdict]}
        </span>
        <span className="text-carix-text/70">{check.message}</span>
        {check.impactUsers != null && check.impactUsers > 0 && (
          <span className="text-carix-text/70">영향 {check.impactUsers}명</span>
        )}
        {check.action &&
          (check.actionLink ? (
            <Link to={check.actionLink} className="text-carix-info underline">
              {check.action}
            </Link>
          ) : (
            <span className="text-carix-info">{check.action}</span>
          ))}
        {check.drillDown && (
          <Link
            to={check.drillDown}
            aria-label={`${check.title} 원본 보기`}
            className="text-carix-text/70 underline"
          >
            원본 보기
          </Link>
        )}
        {Object.keys(check.evidence).length > 0 && (
          <Button
            type="button"
            aria-expanded={open}
            aria-label={`${check.title} 근거`}
            onClick={() => setOpen((o) => !o)}
            variant="link"
            size="xs"
            className="ml-auto text-carix-text/60"
          >
            {open ? "근거 접기" : "근거"}
          </Button>
        )}
      </div>
      {open && (
        <pre className="mt-2 overflow-x-auto rounded bg-carix-bg p-2 text-[11px]">
          {JSON.stringify(check.evidence, null, 2)}
        </pre>
      )}
    </li>
  );
}

export function DataHealthGroupCard({ group }: { group: HealthGroup }) {
  const [showOk, setShowOk] = useState(false);
  const sorted = sortChecks(group.checks);
  const okCount = sorted.filter((c) => c.verdict === "OK").length;
  const visible = showOk ? sorted : sorted.filter((c) => c.verdict !== "OK");
  return (
    <section
      aria-label={group.title}
      className="rounded-xl border border-carix-border bg-carix-surface p-5"
    >
      <h3 className="text-sm font-semibold">
        {group.title}{" "}
        <span className="font-normal text-carix-text/60">
          — {group.question}
        </span>
      </h3>
      <ul className="mt-2">
        {visible.map((c) => (
          <Row key={c.id} check={c} />
        ))}
      </ul>
      {okCount > 0 && (
        <Button
          type="button"
          onClick={() => setShowOk((s) => !s)}
          variant="link"
          size="xs"
          className="mt-2 text-carix-text/60"
        >
          {showOk ? "정상 항목 접기" : `정상 ${okCount}건 보기`}
        </Button>
      )}
    </section>
  );
}
