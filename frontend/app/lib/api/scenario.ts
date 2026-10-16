import { coreGet, coreSend } from "../coreApi";
import type { ScenarioInput } from "../schemas/scenario";

export type FeasibilityStatus = "OK" | "INSUFFICIENT_SAMPLE";
export type DifficultyGrade =
  "EASY" | "REALISTIC" | "CHALLENGING" | "VERY_HIGH_CHALLENGE";
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
  /** 이름 없이 계산했으면 null 이다 — 목록 라벨은 계산 시각으로 만든다(`scenarioLabel.ts`). */
  scenarioName: string | null;
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

/**
 * 저장분의 입력 가정. 요청(`ScenarioInput`)과 달리 이름이 null 로 올 수 있고, 계산에 실제로
 * 쓴 현재 연봉이 함께 실린다.
 */
export type SavedScenarioInputs = Omit<ScenarioInput, "scenarioName"> & {
  scenarioName: string | null;
  currentGrossSalary: number;
};

/** 저장된 시나리오 한 건 (API 규격 §6.3). */
export interface SavedScenario {
  id: string;
  scenarioName: string | null;
  inputs: SavedScenarioInputs;
  result: SimulationResult;
  calculatedAt: string;
}

/**
 * 저장분 전부, 최근 순이다 — 첫 행이 마지막 시나리오라 `GET /latest` 를 따로 부르지 않는다.
 * 없으면 빈 배열(200)이다. 복호화된 금액이 여러 건 실리므로 로그에 남기지 않는다(완료 기준 10번).
 */
export const listScenarios = () => coreGet<SavedScenario[]>("/v1/scenarios");

export const simulate = (input: ScenarioInput) =>
  coreSend<SimulationResult>("POST", "/v1/scenarios/simulate", input);
