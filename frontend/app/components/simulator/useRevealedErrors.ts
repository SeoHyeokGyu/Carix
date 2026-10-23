import { useRef, useState } from "react";

import type { FieldErrors } from "../../lib/fieldErrors";

/**
 * 즉시 계산 중의 칸 오류. 손댄 칸의 오류만 보이고, `flush` 뒤(이동·저장 시도)에는 전부 보인다.
 * 처음 들어오자마자, 또는 연봉만 넣었는데 아직 고르지 않은 질문에 오류가 뜨지 않게 한다.
 */
export function useRevealedErrors() {
  const [errors, setErrors] = useState<FieldErrors>({});
  const revealed = useRef<ReadonlySet<string> | "all">(new Set());

  const touch = (field: string) => {
    if (revealed.current !== "all") {
      revealed.current = new Set([...revealed.current, field]);
    }
  };

  const revealAll = () => {
    revealed.current = "all";
  };

  const pickRevealed = (found: FieldErrors): FieldErrors => {
    const shown = revealed.current;
    if (shown === "all") return found;
    return Object.fromEntries(
      Object.entries(found).filter(([field]) => shown.has(field)),
    );
  };

  return { errors, setErrors, touch, revealAll, pickRevealed };
}
