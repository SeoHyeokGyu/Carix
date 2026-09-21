import { coreGet, coreSend } from "../coreApi";
import type {
  IncomeChangeReason,
  IncomeChangeReasonValue,
  IncomeInput,
} from "../schemas/income";

export interface IncomeResponse {
  id: string;
  grossSalary: number;
  netSalary: number;
  effectiveDate: string;
  /** 표시용. 가장 이른 유효일 행이면 `INITIAL` 이다 — 컬럼값이 아니다. */
  changeReason: IncomeChangeReason | null;
  /** 컬럼 원값. 편집 폼이 이것으로 셀렉트를 채운다. 옵셔널(`?:`)로 두지 않는다 — 픽스처 누락을 tsc 가 잡아야 한다. */
  selectedChangeReason: IncomeChangeReasonValue | null;
}

export const listIncome = () => coreGet<IncomeResponse[]>("/v1/income");

export const createIncome = (input: IncomeInput) =>
  coreSend<IncomeResponse>("POST", "/v1/income", input);

export const updateIncome = (id: string, input: IncomeInput) =>
  coreSend<IncomeResponse>("PUT", `/v1/income/${id}`, input);

export const deleteIncome = (id: string) =>
  coreSend<null>("DELETE", `/v1/income/${id}`);
