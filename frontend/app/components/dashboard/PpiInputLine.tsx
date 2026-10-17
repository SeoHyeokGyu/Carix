import { TextLink } from "@/components/ui/text-link";

import { formatManwon } from "../../lib/dataExplore";
import type { PpiInputs } from "../../lib/ppiInputs";

const MISSING = "미입력";

/**
 * 구매력 카드 하단의 입력값 한 줄과 입력 관리 링크. 판정이 이상해 보일 때 내 입력부터
 * 확인하러 가는 길이다. 조회에 실패하면(`inputs` null) 줄 자체가 없다.
 */
export function PpiInputLine({ inputs }: { inputs: PpiInputs | null }) {
  if (!inputs) return null;
  const salary =
    inputs.grossSalary === null ? MISSING : formatManwon(inputs.grossSalary);
  const expense = inputs.expense
    ? `${formatManwon(inputs.expense.monthlyTotal)}${inputs.expense.hasDefaultFill ? "(일부 평균)" : ""}`
    : MISSING;

  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
      <span className="text-carix-text/70">
        {`세전 연봉 ${salary} · 월 생활비 ${expense}`}
      </span>
      <TextLink
        to="/mypage/records"
        direction="none"
        aria-label="연봉·생활비 수정"
      >
        수정
      </TextLink>
    </div>
  );
}
