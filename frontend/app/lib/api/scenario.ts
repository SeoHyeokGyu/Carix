import { coreGet, coreSend } from "../coreApi";
import type { ScenarioInput } from "../schemas/scenario";

export type FeasibilityStatus = "OK" | "INSUFFICIENT_SAMPLE";
export type DifficultyGrade = "EASY" | "REALISTIC" | "CHALLENGING" | "VERY_HIGH_CHALLENGE";
export type ScenarioType = "ESTIMATED_GAIN" | "ESTIMATED_OPPORTUNITY_COST";

/**
 * 표본이 부족하면(`INSUFFICIENT_SAMPLE`) 백분위·점수·등급·안내문 넷 모두 `null` 이다
 * — 백분위를 임의 분포로 지어내지 않기 위한 서버 규약(API 규격 §6.1)을 타입으로
 * 그대로 옮긴다.
 */
export interface ScenarioFeasibility {
  status: FeasibilityStatus;
  sampleSize: number;
  minRequired: number | null;
  feasibilityScore: number | null;
  targetPercentile: number | null;
  difficultyGrade: DifficultyGrade | null;
  feedbackMessage: string | null;
}

export interface SimulationResult {
  id: string;
  scenarioName: string;
  stayCompensation12m: number;
  moveCompensation12m: number;
  estimatedUnemploymentBenefit: number;
  unemploymentBenefitNote: string;
  difference: number;
  type: ScenarioType;
  typeLabel: string;
  warningNote: string;
  feasibility: ScenarioFeasibility;
}

export interface LatestScenario {
  inputs: ScenarioInput;
  result: SimulationResult;
  calculatedAt: string;
}

/**
 * 저장된 시나리오가 없으면 204 다 — coreGet 은 이 경우 null 을 반환하지만 호출자의
 * 타입을 넓히지 않는다(coreApi.ts 주석). 이 엔드포인트가 실제로 204 를 받는 유일한
 * 곳이라 여기서 명시적으로 `| null` 을 붙인다.
 */
export const getLatestScenario = () => coreGet<LatestScenario | null>("/v1/scenarios/latest");

export const simulate = (input: ScenarioInput) =>
  coreSend<SimulationResult>("POST", "/v1/scenarios/simulate", input);
