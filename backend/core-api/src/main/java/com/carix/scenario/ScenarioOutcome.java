package com.carix.scenario;

/**
 * 12개월 보상 비교 결과 (도메인 정의서 §4.2).
 *
 * @param stayCompensation12m 잔류 시 12개월 누적 세전 보상
 * @param moveCompensation12m 이직 시 12개월 누적 세전 보상. 공백과 실업급여, 부대비용이 반영된다
 * @param estimatedUnemploymentBenefit 추정 실업급여 총액
 * @param difference {@code move − stay}
 * @param type {@code ESTIMATED_GAIN} 또는 {@code ESTIMATED_OPPORTUNITY_COST}
 * @param typeLabel 화면 표기. 규격이 이 두 문구로 고정한다
 * @param unemploymentBenefitNote 실업급여 산출 방식 안내. 수급 자격이 없으면 `실업급여 미반영`, 있으면 가정치 기준 시점을 알린다
 */
public record ScenarioOutcome(
    long stayCompensation12m,
    long moveCompensation12m,
    long estimatedUnemploymentBenefit,
    long difference,
    String type,
    String typeLabel,
    String unemploymentBenefitNote) {}
