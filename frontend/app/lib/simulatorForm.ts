import { parseAmountInput } from "./amountInput";
import { toUserMessage } from "./apiError";
import { UnauthorizedError } from "./coreApi";

const WON_PER_MAN = 10_000;

/** 만원 단위 입력칸 → 원. 비어 있으면 null. */
export function manInputToWon(value: string): number | null {
  const man = parseAmountInput(value);
  return man === null ? null : man * WON_PER_MAN;
}

/** 원 → 만원 입력칸 문자열(콤마 포함). null 이면 빈칸. */
export function wonToManInput(won: number | null | undefined): string {
  return won == null
    ? ""
    : Math.round(won / WON_PER_MAN).toLocaleString("ko-KR");
}

/** 저장·조회 실패를 화면에 보일 문장으로 바꾼다. 세션 만료는 일반 문구로 가리지 않는다. */
export function describeError(error: unknown): string {
  if (error instanceof UnauthorizedError) {
    return "로그인이 만료되었습니다. 다시 로그인해 주세요.";
  }
  return toUserMessage(error);
}
