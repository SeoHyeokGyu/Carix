import type { EvaluateRequest } from "./api/scenario";

export interface ScenarioTemplate {
  id: string;
  name: string;
  description: string;
  badge: string;
  /**
   * 사용자의 현재 연봉(원 단위)을 바탕으로 새 회사 제안(move) 파라미터를 생성한다.
   * 현재 연봉이 없거나 0 이하일 경우 5,000만 원을 기본 기준으로 삼는다.
   */
  createMove: (currentSalary: number) => NonNullable<EvaluateRequest["move"]>;
}

const DEFAULT_FALLBACK_SALARY = 50_000_000;
const ROUND_UNIT = 100_000;

function roundSalary(amount: number): number {
  return Math.round(amount / ROUND_UNIT) * ROUND_UNIT;
}

export const SCENARIO_TEMPLATES: readonly ScenarioTemplate[] = [
  {
    id: "jump",
    name: "안정적 연봉 점프",
    description: "기본급 15% 인상, 성과급 예상 10%, 공백기 없이 환승 이직",
    badge: "+15%",
    createMove: (currentSalary) => {
      const base = currentSalary > 0 ? currentSalary : DEFAULT_FALLBACK_SALARY;
      const gross = roundSalary(base * 1.15);
      const perfBonus = roundSalary(base * 0.1);
      return {
        grossSalary: gross,
        unemploymentMonths: 0,
        oneTimeCost: 0,
        benefitEligible: false,
        bonusIncluded: false,
        fixedBonusAnnual: 0,
        performanceBonusExpected: perfBonus,
        signingBonus: 0,
        stockVesting12m: 0,
      };
    },
  },
  {
    id: "startup",
    name: "스타트업 보상 (스톡·사이닝)",
    description:
      "기본급 5% 인상 + 사이닝 1,000만 원 + RSU 연 1,000만 원 베스팅",
    badge: "지분·보너스",
    createMove: (currentSalary) => {
      const base = currentSalary > 0 ? currentSalary : DEFAULT_FALLBACK_SALARY;
      const gross = roundSalary(base * 1.05);
      return {
        grossSalary: gross,
        unemploymentMonths: 0,
        oneTimeCost: 0,
        benefitEligible: false,
        bonusIncluded: false,
        fixedBonusAnnual: 0,
        performanceBonusExpected: 0,
        signingBonus: 10_000_000,
        stockVesting12m: 10_000_000,
      };
    },
  },
  {
    id: "rest-and-jump",
    name: "충전 후 이직 (2개월 공백)",
    description:
      "기본급 10% 인상, 2개월 휴식 공백 및 이사/이직 준비비 200만 원",
    badge: "공백 2개월",
    createMove: (currentSalary) => {
      const base = currentSalary > 0 ? currentSalary : DEFAULT_FALLBACK_SALARY;
      const gross = roundSalary(base * 1.1);
      return {
        grossSalary: gross,
        unemploymentMonths: 2,
        oneTimeCost: 2_000_000,
        benefitEligible: false,
        bonusIncluded: false,
        fixedBonusAnnual: 0,
        performanceBonusExpected: 0,
        signingBonus: 0,
        stockVesting12m: 0,
      };
    },
  },
  {
    id: "horizontal",
    name: "워라밸 수평 이동",
    description:
      "기본급 동결, 공백기 없이 실수령 및 실질 구매력(물가) 방어 확인",
    badge: "동결",
    createMove: (currentSalary) => {
      const base = currentSalary > 0 ? currentSalary : DEFAULT_FALLBACK_SALARY;
      return {
        grossSalary: base,
        unemploymentMonths: 0,
        oneTimeCost: 0,
        benefitEligible: false,
        bonusIncluded: false,
        fixedBonusAnnual: 0,
        performanceBonusExpected: 0,
        signingBonus: 0,
        stockVesting12m: 0,
      };
    },
  },
] as const;
