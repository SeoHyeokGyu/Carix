import { z } from "zod";

import { GROSS_SALARY_RANGE_TEXT } from "./options";

/**
 * 인상률 범위는 기능 명세 §3(-50~+100)을 쓴다 — API 규격(-100~100)보다 좁아
 * 양쪽을 통과한다 (설계 §8-f).
 */
export const scenarioSchema = z.object({
  scenarioName: z.string().max(100).optional(),
  stayExpectedRaiseRate: z.number().min(-50).max(100),
  moveTargetGrossSalary: z.number().int().min(10_000_000).max(3_000_000_000),
  moveOneTimeCost: z.number().int().min(0).max(100_000_000),
  expectedUnemploymentMonths: z.number().int().min(0).max(6),
  isEligibleForUnemploymentBenefit: z.boolean(),
});

export type ScenarioInput = z.infer<typeof scenarioSchema>;

/**
 * 화면(simulator.tsx)이 쓰는 필드별 오류 문구. 규칙(위 스키마)과 그 규칙을
 * 사람이 읽을 문장으로 바로 옆에 두어, 범위가 바뀌었는데 문구만 옛날 값으로
 * 남는 것을 막는다. 키는 `keyof ScenarioInput` 에 묶지 않는다 —
 * `isEligibleForUnemploymentBenefit` 을 라디오 미선택 상태로 남겨도 걸리는
 * 화면 쪽 메시지가 스키마 필드가 아닌 자리에도 필요할 수 있어(현재는 아니지만)
 * profile.tsx 이전 설계와 같은 인덱스 시그니처를 유지한다.
 */
export const SCENARIO_FIELD_MESSAGES: Record<string, string> = {
  scenarioName: "시나리오 이름은 100자 이하로 입력해 주세요.",
  stayExpectedRaiseRate: "예상 인상률은 -50~100 사이로 입력해 주세요.",
  moveTargetGrossSalary: `이직 목표 세전 연봉은 ${GROSS_SALARY_RANGE_TEXT}`,
  moveOneTimeCost: "이직 일회성 비용은 0원~1억 원 사이로 입력해 주세요.",
  expectedUnemploymentMonths: "예상 구직 공백은 0~6개월 사이로 입력해 주세요.",
  isEligibleForUnemploymentBenefit: "실업급여 수급 자격 여부를 선택해 주세요.",
};
