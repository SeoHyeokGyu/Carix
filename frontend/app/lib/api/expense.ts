import { coreGet, coreSend } from "../coreApi";

/**
 * `USER_DEFINED` 는 사용자가 직접 입력한 값, `DEFAULT_CPI` 는 미입력이라 서버가
 * 통계청 평균 소비지출 비중으로 보간한 값이다 — 화면은 이 값을 그대로 뱃지로
 * 그린다. 클라이언트가 스스로 계산해 뱃지를 만들지 않는다.
 */
export type ExpenseItemStatus = "USER_DEFINED" | "DEFAULT_CPI";

export interface ExpenseHistoryItem {
  category: string;
  amount: number;
  status: ExpenseItemStatus;
}

export interface ExpenseHistoryResponse {
  baseYearMonth: string;
  items: ExpenseHistoryItem[];
  weightStatus: string;
}

export const listExpenses = () =>
  coreGet<ExpenseHistoryResponse[]>("/v1/expenses");

/**
 * 미입력 카테고리는 `items` 에서 아예 빼서 넘겨야 한다 — 호출부(화면)의 책임이다.
 * 여기서 0 으로 채우거나 누락분을 보완하지 않는다.
 */
export const putExpenses = (
  baseYearMonth: string,
  items: { category: string; amount: number }[],
) =>
  coreSend<ExpenseHistoryResponse>("PUT", `/v1/expenses/${baseYearMonth}`, {
    items,
  });

/**
 * 금액의 출처. `NONE` 은 입력도 전파할 과거 월도 없어 절대 금액 기준이 없다는 뜻이다 —
 * 그때 `amount` 는 전부 `null` 이고 지수는 가중치만으로 계산된다(API 규격 §7.6).
 */
export type AmountBasis = "USER_INPUT" | "PROPAGATED" | "NONE";

export interface AppliedExpenseCategory {
  category: string;
  amount: number | null;
  /** 기본 가중치 정본 값이다. 실제로 "적용된" 비중인 것은 `NONE` 일 때뿐이다. */
  weight: number;
  weightStatus: "USER_DEFINED" | "DEFAULT_CPI" | "PROPAGATED";
}

export interface AppliedExpenseResponse {
  baseYearMonth: string;
  weightVersion: number;
  weightBasePeriod: string;
  amountBasis: AmountBasis;
  categories: AppliedExpenseCategory[];
}

/** 기준월을 생략하면 서버가 최신 이력 월로 대체한다 — 검토 화면은 늘 당월을 넘긴다. */
export const getAppliedExpenses = (baseYearMonth: string) =>
  coreGet<AppliedExpenseResponse>(
    `/v1/expenses/applied?baseYearMonth=${baseYearMonth}`,
  );
