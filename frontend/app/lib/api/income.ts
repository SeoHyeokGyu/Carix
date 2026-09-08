import { coreGet, coreSend } from "../coreApi";
import type { IncomeInput } from "../schemas/income";

export interface IncomeResponse {
  id: string;
  grossSalary: number;
  netSalary: number;
  effectiveDate: string;
}

export const listIncome = () => coreGet<IncomeResponse[]>("/v1/income");

export const createIncome = (input: IncomeInput) =>
  coreSend<IncomeResponse>("POST", "/v1/income", input);

export const updateIncome = (id: string, input: IncomeInput) =>
  coreSend<IncomeResponse>("PUT", `/v1/income/${id}`, input);

export const deleteIncome = (id: string) => coreSend<null>("DELETE", `/v1/income/${id}`);
