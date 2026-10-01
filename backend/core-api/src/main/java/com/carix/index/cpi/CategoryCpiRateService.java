package com.carix.index.cpi;

import com.carix.expense.domain.ExpenseCategoryEnum;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 품목 CPI 12대 분류를 Carix 6개 카테고리 전년 동월 대비 변화율로 합성한다.
 *
 * <p><b>사용자 가중치를 모른다.</b> 물가 기여도 뱃지(c12)가 처음 쓰고, 구매력 지수 본 엔진(a2)·임금 vs 개인물가(a10)가 같은 합성을 재사용하는 경계다.
 *
 * <p>하위 가중치는 변화율에만 곱한다. 지수 수준을 평균하면 기준시점이 조용히 재설정된다 ({@code default-expense-weights.yml} 주석).
 */
@Service
@RequiredArgsConstructor
public class CategoryCpiRateService {

  /** 통계청 공표 시차상 정상 운영의 최대 시차. 넘으면 수집이 멈춘 것이다 (c12 설계 §4). */
  public static final int MAX_LAG_MONTHS = 2;

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final int RATE_SCALE = 10;

  private final CpiObservationRecordRepository repository;
  private final CpiCategoryConfig config;

  @Transactional(readOnly = true)
  public Optional<CategoryCpiRates> yearOverYear(String targetYearMonth) {
    YearMonth target = YearMonth.parse(targetYearMonth, YM);
    Optional<String> latest =
        repository
            .findFirstByBaseYearMonthLessThanEqualOrderByBaseYearMonthDesc(targetYearMonth)
            .map(CpiObservationRecord::getBaseYearMonth);
    if (latest.isEmpty()) {
      return Optional.empty();
    }

    YearMonth cpiMonth = YearMonth.parse(latest.get(), YM);
    if (cpiMonth.isBefore(target.minusMonths(MAX_LAG_MONTHS))) {
      return Optional.empty();
    }

    String current = YM.format(cpiMonth);
    String yearAgo = YM.format(cpiMonth.minusYears(1));
    Map<String, BigDecimal> now = new HashMap<>();
    Map<String, BigDecimal> before = new HashMap<>();
    for (CpiObservationRecord row : repository.findByBaseYearMonthIn(List.of(current, yearAgo))) {
      (row.getBaseYearMonth().equals(current) ? now : before)
          .put(row.getCategory(), row.getCpiValue());
    }

    Map<ExpenseCategoryEnum, BigDecimal> rates = new EnumMap<>(ExpenseCategoryEnum.class);
    config
        .categories()
        .forEach(
            (category, definition) ->
                rate(definition, now, before).ifPresent(rate -> rates.put(category, rate)));
    return Optional.of(new CategoryCpiRates(current, rates));
  }

  /** 하위 계열 하나라도 두 시점 중 한쪽이 없으면 계산하지 않는다. 남은 가중치를 재조정하면 카테고리의 뜻이 조용히 바뀐다. */
  private static Optional<BigDecimal> rate(
      CpiCategoryConfig.CategoryDefinition definition,
      Map<String, BigDecimal> now,
      Map<String, BigDecimal> before) {
    BigDecimal sum = BigDecimal.ZERO;
    for (CpiCategoryConfig.SubSeries series : definition.subSeries()) {
      BigDecimal current = now.get(series.cpiCode());
      BigDecimal previous = before.get(series.cpiCode());
      if (current == null || previous == null || previous.signum() <= 0) {
        return Optional.empty();
      }
      BigDecimal change =
          current.divide(previous, RATE_SCALE, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
      sum = sum.add(series.weight().multiply(change));
    }
    return Optional.of(sum);
  }
}
