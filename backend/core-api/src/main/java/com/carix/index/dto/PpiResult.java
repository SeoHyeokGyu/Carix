package com.carix.index.dto;

import com.carix.index.enums.ExpenseWeightStatus;
import com.carix.index.enums.IndexStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 구매력 지수 산출 결과 (Member A → Member C).
 *
 * @param baseYearMonth 산출 대상 월. {@code YYYYMM} 6자다 — core 스키마 전체가 이 규약이다
 * @param value 지수값. 계산 불가면 {@code null} 이다. 0 이나 100 으로 채우지 않는다
 * @param status 값이 없거나 온전하지 않은 이유. {@link IndexStatus#INSUFFICIENT_HISTORY} 는 비교 기간이 12개월 미만이라는 뜻이고
 *     값이 있다
 * @param weightStatus 소비 가중치 출처. 대시보드 뱃지가 이 값을 그린다
 * @param indexBaseYearMonth 지수 기준월({@code = 100}). 연봉이 처음 있는 달과 CPI 가 처음 있는 달 중 늦은 달이다 (§4.3). 계산
 *     불가면 {@code null}
 * @param cpiAsOf 사용한 CPI 원천의 기준일. {@code index_snapshot.rationale} 의 `원천 기준일` 이 된다. 계산 불가면 {@code
 *     null}
 * @param incomeIndex 소득 지수(기준월 = 100). {@code value} 의 분자다. {@code value} 가 없으면 {@code null}
 * @param personalPriceIndex 개인물가 지수(기준월 = 100). {@code value} 의 분모다. {@code value} 가 없으면 {@code
 *     null}
 */
public record PpiResult(
    String baseYearMonth,
    BigDecimal value,
    IndexStatus status,
    ExpenseWeightStatus weightStatus,
    String indexBaseYearMonth,
    LocalDate cpiAsOf,
    BigDecimal incomeIndex,
    BigDecimal personalPriceIndex) {

  public PpiResult {
    IndexValueContract.PPI.requireArgument(baseYearMonth, value, status);
  }

  /** 구성값 없이 만든다. 값이 없는 결과와 구성값이 필요 없는 테스트 대역이 쓴다. */
  public PpiResult(
      String baseYearMonth,
      BigDecimal value,
      IndexStatus status,
      ExpenseWeightStatus weightStatus,
      String indexBaseYearMonth,
      LocalDate cpiAsOf) {
    this(baseYearMonth, value, status, weightStatus, indexBaseYearMonth, cpiAsOf, null, null);
  }

  /** 입력이 없거나 산출기가 준비되지 않은 상태. */
  public static PpiResult notAvailable(String baseYearMonth) {
    return new PpiResult(
        baseYearMonth,
        null,
        IndexStatus.NOT_AVAILABLE,
        ExpenseWeightStatus.DEFAULT_CPI,
        null,
        null);
  }
}
