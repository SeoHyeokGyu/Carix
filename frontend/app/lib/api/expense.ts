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

export const listExpenses = () => coreGet<ExpenseHistoryResponse[]>("/v1/expenses");

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
