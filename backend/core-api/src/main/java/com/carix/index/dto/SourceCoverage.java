package com.carix.index.dto;

import java.math.BigDecimal;

/**
 * 원천 하나에 대한 동료집단 완화 결과.
 *
 * <p>완화는 원천마다 독립으로 돈다 — 같은 조회에서 JOBKOREA 는 Level 1, SARAMIN 은 Level 5 에 안착할 수 있다. 그래서 단일 값이 아니라 원천별
 * 목록이다 (도메인 정의서 §2.3).
 *
 * @param source 원천 코드 (`JOBKOREA`, `SARAMIN`, `WANTED`, `JUMPIT`)
 * @param fallbackLevel 안착한 완화 단계 (1~5). 낮을수록 조건이 정확하다
 * @param sampleCount 동료집단 셀의 공고 수. 스킬 행 표본이 아니다
 * @param successful 이 원천이 완화 Level 5 까지 가서도 최소 표본을 채웠는가. {@code false} 면 {@code sampleCount} 는 끝까지
 *     밀어 모은 잔여 표본이라 지수 결합에 넣지 않는다. 값만으로는 성공과 구분되지 않아 명시 필드로 둔다 — {@code fallbackLevel} 은 성공한 Level
 *     5 와 실패가 똑같이 5 다
 * @param appliedConditions 화면에 그대로 쓸 비교 조건 문구. 예: `서울 전체 / 경력 4~7년(MID) / 회사규모 무관`
 * @param salaryMissingRatePercent 공개연봉 결측률. 단위는 퍼센트다 (0.00 ~ 100.00) — 비율이 아니다
 */
public record SourceCoverage(
    String source,
    int fallbackLevel,
    int sampleCount,
    boolean successful,
    String appliedConditions,
    BigDecimal salaryMissingRatePercent) {}
