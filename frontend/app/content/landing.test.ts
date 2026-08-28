import { describe, expect, it } from "vitest";
import {
  HERO_INTRO,
  CORE_INDEX_CARDS,
  DATA_FLOW_NODES,
  FORMULAS,
  FEATURES,
  RESULT_EXAMPLE,
  DISCLAIMER,
  BETA_NOTICE,
} from "./landing";

describe("landing content", () => {
  it("히어로 3가지 질문을 README 원문 그대로 포함한다", () => {
    expect(HERO_INTRO.questions).toEqual([
      "내 실질 구매력은 좋아지고 있는가?",
      "시장에서 내 스킬의 가치는 오르고 있는가?",
      "지금 남고, 옮기고, 배우는 선택 중 무엇이 유리한가?",
    ]);
  });

  it("핵심 지수 카드 3개를 정의한다", () => {
    expect(CORE_INDEX_CARDS).toHaveLength(3);
    expect(CORE_INDEX_CARDS.map((c) => c.title)).toEqual([
      "구매력 지수",
      "시장가치 지수",
      "결정 신호",
    ]);
  });

  it("데이터 원천 노드에 잡코리아/사람인 실명을 노출하지 않는다", () => {
    const allText = DATA_FLOW_NODES.map((n) => `${n.label} ${n.detail}`).join(
      " ",
    );
    expect(allText).not.toContain("잡코리아");
    expect(allText).not.toContain("사람인");
    expect(allText).toContain("채용공고 데이터");
  });

  it("구매력 계산식에 정확한 부호·비율 표현을 포함한다", () => {
    const purchasingPower = FORMULAS[0];
    expect(purchasingPower.formula).toContain(
      "실질 구매력 지수 = 명목소득 지수 ÷ 개인물가 지수 × 100",
    );
  });

  it("스킬 시장가치 계산식 note에 연봉 격차 부호 규약을 정확히 포함한다", () => {
    const marketValue = FORMULAS[1];
    expect(marketValue.note).toContain(
      "연봉 격차는 사용자 연봉이 시장 중앙값보다 낮을 때 양수",
    );
  });

  it("주요 기능 항목에 비중첩 비교 문구를 정확히 포함한다", () => {
    const skillFeature = FEATURES.find((f) => f.includes("스킬"));
    expect(skillFeature).toContain(
      "최근 3개월을 그 이전 12개월과 비교, 비중첩",
    );
  });

  it("결과 예시 지수 값을 README와 동일하게 포함한다", () => {
    expect(RESULT_EXAMPLE).toContain("구매력 지수    94  하락");
    expect(RESULT_EXAMPLE).toContain("시장가치 지수 117  상승");
  });

  it("면책 문구를 README 원문 그대로 정의한다", () => {
    expect(DISCLAIMER).toBe(
      "수치는 진단 근거와 표본 수를 함께 제공하며, 확정적인 연봉 예측이나 재무 조언으로 표현하지 않습니다.",
    );
  });

  it("시작 안내에 직군 범위와 가입 방식을 담는다", () => {
    expect(BETA_NOTICE).toContain("개발·데이터·AI 직군");
    expect(BETA_NOTICE).toContain("이메일과 비밀번호");
  });
});
