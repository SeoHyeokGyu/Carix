import { z } from "zod";

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
