import type { EvaluationResponse } from "../../lib/api/scenario";

/** 계산 근거에 실린 가정. 접힘이 기본이다. */
export function AssumptionsPanel({
  assumptions,
}: {
  assumptions: EvaluationResponse["assumptions"];
}) {
  if (assumptions.length === 0) return null;
  return (
    <details className="rounded-lg border px-4 py-3 text-sm">
      <summary className="cursor-pointer font-medium">계산 근거·가정</summary>
      <ul className="mt-3 flex list-disc flex-col gap-1.5 pl-5 text-muted-foreground">
        {assumptions.map((item) => (
          <li key={item.code}>{item.text}</li>
        ))}
      </ul>
    </details>
  );
}
