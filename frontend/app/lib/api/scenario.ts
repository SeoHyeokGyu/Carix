import { z } from "zod";

import { coreGet, coreSend } from "../coreApi";

const amount = z.number().int().nonnegative().nullish();

const staySchema = z.object({
  raisePercent: z.number(),
  currentGrossOverride: amount,
  performanceBonusEstimate: amount,
  stockVesting12m: amount,
  stockUnvested: amount,
});

const moveSchema = z.object({
  grossSalary: z.number().int().nonnegative(),
  unemploymentMonths: z.number().int().min(0).max(6),
  oneTimeCost: z.number().int().nonnegative(),
  benefitEligible: z.boolean(),
  bonusIncluded: z.boolean(),
  fixedBonusAnnual: amount,
  performanceBonusExpected: amount,
  signingBonus: amount,
  stockVesting12m: amount,
});

/** `POST /v1/scenarios/evaluate` 본문이자 초안·저장 입력 (API 규격 §6.6). `move` 가 없으면 잔류만 본다. */
export const evaluateRequestSchema = z.object({
  stay: staySchema,
  move: moveSchema.nullish(),
});
export type EvaluateRequest = z.infer<typeof evaluateRequestSchema>;

const inflationBasisSchema = z.object({
  governmentPercent: z.number(),
  /** 개인물가를 계산할 수 없으면 null. */
  personalPercent: z.number().nullable(),
  gapPp: z.number().nullable(),
  topGapCategory: z.string().nullable(),
  similar: z.boolean(),
  asOf: z.string(),
  source: z.string(),
});
export type InflationBasis = z.infer<typeof inflationBasisSchema>;

const feasibilitySchema = z.object({
  status: z.enum(["OK", "INSUFFICIENT_SAMPLE"]),
  sampleSize: z.number(),
  minRequired: z.number().nullable(),
  feasibilityScore: z.number().nullable(),
  targetPercentile: z.number().nullable(),
  difficultyGrade: z
    .enum(["EASY", "REALISTIC", "CHALLENGING", "VERY_HIGH_CHALLENGE"])
    .nullable(),
  feedbackMessage: z.string().nullable(),
});
export type Feasibility = z.infer<typeof feasibilitySchema>;

const rangeSchema = z.object({ min: z.number(), max: z.number() });

const moveViewSchema = z.object({
  stayCumulative: z.array(z.number()),
  moveCumulative: z.array(z.number()),
  crossoverMonth: z.number().int().nullable(),
  firstYear: z.object({
    salaryDiff: z.number(),
    gap: z.number(),
    oneTimeCost: z.number(),
    benefit: z.number(),
    total: z.number(),
  }),
  expectedRange: rangeSchema,
  stock: z
    .object({
      stayVesting12m: z.number().nullable(),
      forfeitedUnvested: z.number().nullable(),
      moveVesting12m: z.number().nullable(),
    })
    .nullable(),
  equalizingRaisePercent: z.number().nullable(),
  feasibility: feasibilitySchema,
});

export const evaluationResponseSchema = z.object({
  stay: z.object({
    realChangePercent: z.number(),
    realChangeGovernmentPercent: z.number(),
    breakevenPersonalPercent: z.number().nullable(),
    breakevenGovernmentPercent: z.number().nullable(),
    marketGapPercent: z.number().nullable(),
  }),
  move: moveViewSchema.nullable(),
  inflation: inflationBasisSchema,
  assumptions: z.array(z.object({ code: z.string(), text: z.string() })),
  unreflected: z.array(z.string()),
  severance: z
    .object({ monthsToAnniversary: z.number(), estimatedAmount: z.number() })
    .nullable(),
  typeLabel: z.string().nullable(),
});
export type EvaluationResponse = z.infer<typeof evaluationResponseSchema>;

export interface ScenarioContext {
  currentAnnualGross: number | null;
  monthlyNet: number | null;
  hireDate: string | null;
  bonusIncluded: boolean;
  fixedBonusAnnual: number | null;
  defaultRaisePercent: number | null;
  performanceBonusRange: { min: number; average: number; years: number } | null;
  inflation: InflationBasis;
  /** 세전·명목 기준 시장 중앙값 격차(%). 산출 불가면 null. */
  marketGapPercent: number | null;
}

/** 엔진 1 로 저장된 행. 저장한 값 그대로이며 다시 계산하지 않는다 (API 규격 §6.3). */
export interface LegacyScenario {
  inputs: {
    scenarioName: string | null;
    currentGrossSalary: number;
    stayExpectedRaiseRate: number;
    moveTargetGrossSalary: number;
    moveOneTimeCost: number;
    expectedUnemploymentMonths: number;
    isEligibleForUnemploymentBenefit: boolean;
  };
  result: {
    id: string;
    scenarioName: string | null;
    stayCompensation12m: number;
    moveCompensation12m: number;
    estimatedUnemploymentBenefit: number;
    unemploymentBenefitNote: string;
    difference: number;
    type: "ESTIMATED_GAIN" | "ESTIMATED_OPPORTUNITY_COST";
    typeLabel: string;
    warningNote: string;
    feasibility: Feasibility;
  };
}

/** `engineVersion` 2 는 `request`·`result`, 1 은 `legacy` 만 채워진다. */
/** 입력 가정과 12개월 뒤 실제 소득의 비교. 상태에 해당하지 않는 필드는 null 이다. */
export interface ScenarioVerification {
  status:
    | "WAITING"
    | "NO_INCOME_AFTER"
    | "NO_OFFER"
    | "MOVE_COMPARED"
    | "NO_BASELINE"
    | "UNKNOWN_REASON"
    | "STAY_COMPARED";
  monthsLeft: number | null;
  expectedRaisePercent: number | null;
  actualRaisePercent: number | null;
  offerAnnualGross: number | null;
  actualAnnualGross: number | null;
}

export interface SavedScenario {
  id: string;
  name: string | null;
  engineVersion: 1 | 2;
  calculatedAt: string;
  request: EvaluateRequest | null;
  result: EvaluationResponse | null;
  legacy: LegacyScenario | null;
  verification?: ScenarioVerification | null;
}

export const getContext = () =>
  coreGet<ScenarioContext>("/v1/scenarios/context");

/** 응답이 약속한 모양이 아니면 숫자를 그리기 전에 여기서 던진다. */
export const evaluate = async (req: EvaluateRequest) =>
  evaluationResponseSchema.parse(
    await coreSend<unknown>("POST", "/v1/scenarios/evaluate", req),
  );

/** 사용자가 저장을 누를 때만 부른다. 서버가 입력을 다시 평가해 결과와 함께 저장한다. */
export const saveScenario = (name: string | null, request: EvaluateRequest) =>
  coreSend<SavedScenario>("POST", "/v1/scenarios", { name, request });

export const putDraft = (req: EvaluateRequest) =>
  coreSend<null>("PUT", "/v1/scenarios/draft", req);

/** 초안이 없으면 서버가 204 를 주고 래퍼가 null 로 돌려준다. */
export const getDraft = async (): Promise<EvaluateRequest | null> => {
  const draft = await coreGet<unknown>("/v1/scenarios/draft");
  return draft === null ? null : evaluateRequestSchema.parse(draft);
};

/**
 * 저장분 전부, 최근 순. 없으면 빈 배열이다. 복호화된 금액이 여러 건 실리므로 로그에 남기지 않는다.
 */
export const listScenarios = () => coreGet<SavedScenario[]>("/v1/scenarios");

export const getFeasibility = (targetGross: number) =>
  coreGet<Feasibility>(
    `/v1/scenarios/feasibility?targetGross=${encodeURIComponent(targetGross)}`,
  );
