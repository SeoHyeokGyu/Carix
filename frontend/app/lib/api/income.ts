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
  /** 연봉 계약 금액에 상여가 들어 있는가. `false` 면 `fixedBonusAnnual` 을 따로 받는다. */
  bonusIncluded: boolean;
  /** 따로 받는 연간 고정상여(원). `bonusIncluded` 가 `true` 면 항상 `null` 이다. */
  fixedBonusAnnual: number | null;
}

export interface BonusTermsInput {
  applyFromIncomeId: string;
  bonusIncluded: boolean;
  fixedBonusAnnual: number | null;
}

export interface BonusResponse {
  id: string;
  /** `yyyyMM` */
  paidMonth: string;
  amount: number;
}

export interface BonusInput {
  paidMonth: string;
  amount: number;
}

export const listIncome = () => coreGet<IncomeResponse[]>("/v1/income");

export const createIncome = (input: IncomeInput) =>
  coreSend<IncomeResponse>("POST", "/v1/income", input);

export const updateIncome = (id: string, input: IncomeInput) =>
  coreSend<IncomeResponse>("PUT", `/v1/income/${id}`, input);

export const deleteIncome = (id: string) =>
  coreSend<null>("DELETE", `/v1/income/${id}`);

export const updateBonusTerms = (input: BonusTermsInput) =>
  coreSend<IncomeResponse[]>("PUT", "/v1/income/bonus-terms", input);

export const listBonuses = () => coreGet<BonusResponse[]>("/v1/income/bonuses");

export const createBonus = (input: BonusInput) =>
  coreSend<BonusResponse>("POST", "/v1/income/bonuses", input);

/** 204 본문 없음 — `coreApi` 가 `null` 로 돌려준다. */
export const deleteBonus = (id: string) =>
  coreSend<null>("DELETE", `/v1/income/bonuses/${id}`);
