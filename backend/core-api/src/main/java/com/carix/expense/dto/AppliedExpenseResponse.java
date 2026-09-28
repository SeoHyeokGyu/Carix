package com.carix.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 그 달 지수 계산에 <b>실제로 적용된</b> 카테고리별 소비 금액과 가중치 (`US-O6`, `S-09` 검토 화면).
 *
 * <p>통계청 평균을 그대로 내려주는 응답이 아니다. {@link com.carix.expense.service.ExpenseWeightResolver} 가 입력·전파·보간 중
 * 무엇을 썼는지에 따라 답이 달라지고, {@link #amountBasis} 가 그것을 말한다.
 *
 * <p><b>금액이 없을 수 있다.</b> 사용자가 소비를 한 번도 입력하지 않았고 전파할 과거 월도 없으면 절대 금액 기준이 없다 — 지수는 가중치만으로 계산된다. 그때
 * {@code amount} 는 {@code null} 이고 0 으로 채우지 않는다. 0 을 내려주면 "한 달 주거비 0원" 이라는 거짓이 되고, 도메인 정의서가 금지한 임의
 * 수치다. 그 경우 화면이 보여줄 수 있는 유일한 "적용된 것" 이 {@code weight} 다.
 *
 * @param baseYearMonth 기준 년월 {@code YYYYMM}
 * @param weightVersion 기본 가중치 정본({@code config/default-expense-weights.yml})의 버전
 * @param weightBasePeriod 그 가중치의 통계 기준 연도
 * @param amountBasis 금액의 출처. {@code USER_INPUT}·{@code PROPAGATED}·{@code NONE}
 * @param categories 여섯 카테고리 전부. 순서는 {@code ExpenseCategoryEnum} 선언 순서다
 */
@Schema(description = "그 달에 실제로 적용된 소비 금액·가중치")
public record AppliedExpenseResponse(
    @Schema(description = "기준 년월", example = "202609") String baseYearMonth,
    @Schema(description = "기본 가중치 정본 버전", example = "1") int weightVersion,
    @Schema(description = "가중치의 통계 기준 연도", example = "2025") String weightBasePeriod,
    @Schema(
            description =
                "금액 출처. USER_INPUT=사용자 입력(미입력분은 입력 합계로 보간), PROPAGATED=과거 완전 입력 월 전파,"
                    + " NONE=절대 금액 기준 없음(금액 null, 가중치만 적용)",
            example = "USER_INPUT")
        String amountBasis,
    @Schema(description = "카테고리별 적용 결과") List<Category> categories) {

  /**
   * @param category {@code ExpenseCategoryEnum} 이름
   * @param amount 적용된 월 지출액(원). 금액 기준이 없으면 {@code null}
   * @param weight 적용된 카테고리 가중치
   * @param weightStatus {@code USER_DEFINED}·{@code DEFAULT_CPI}·{@code PROPAGATED}
   */
  @Schema(description = "카테고리 한 건의 적용 결과")
  public record Category(
      @Schema(description = "소비 카테고리", example = "HOUSING") String category,
      @Schema(description = "적용된 금액(원). 기준 없으면 null", example = "511000") Long amount,
      @Schema(description = "적용된 가중치", example = "0.1795") BigDecimal weight,
      @Schema(description = "금액의 출처 상태", example = "USER_DEFINED") String weightStatus) {}
}
