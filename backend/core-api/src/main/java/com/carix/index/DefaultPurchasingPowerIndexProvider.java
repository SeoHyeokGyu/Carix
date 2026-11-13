package com.carix.index;

import com.carix.expense.domain.WeightStatusEnum;
import com.carix.expense.service.ExpenseWeightResolver;
import com.carix.expense.service.MonthlyWeights;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.cpi.CategoryCpiRateService;
import com.carix.index.dto.PpiResult;
import com.carix.index.enums.ExpenseWeightStatus;
import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.YearMonths;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매력 지수 = 소득지수 ÷ 개인물가 지수 × 100 (기능 명세 §12.2).
 *
 * <ul>
 *   <li>기준월: 연봉이 처음 있는 달과 CPI 가 처음 있는 달 중 늦은 달. 기준 소득은 그 달까지 유효한 마지막 연봉이다. 기준월 이전 달은 NOT_AVAILABLE
 *       이다. 소비 가중치는 기본값이 항상 있다.
 *   <li>개인물가: 기준월부터 그 달 소비 가중치로 가중한 카테고리 전월 대비 CPI 변화율을 누적한다.
 *   <li>현재월 CPI 가 아직 없으면 허용 시차({@link CategoryCpiRateService#MAX_LAG_MONTHS}) 안의 마지막 CPI 월까지 누적하고
 *       {@code cpiAsOf} 로 밝힌다. 없는 달의 물가를 지어내지 않는다.
 *   <li>소득은 {@link EffectiveIncome} — 계약 금액 밖에서 따로 받는 고정상여를 연액/12 로 매달 더한다.
 *   <li>연봉이 1건이어도 계산한다 — 연봉이 그대로면 물가만큼 내려간다. 12개월 미만은 값이 있고 상태가 비교 기간 짧음이다.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DefaultPurchasingPowerIndexProvider implements PurchasingPowerIndexProvider {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final int FULL_HISTORY_MONTHS = 12;

  /** 산식 버전. 2 부터 별도 고정상여를 연액/12 로 소득에 반영한다. 스냅샷 근거에 남는다. */
  public static final String FORMULA_VERSION = "2";

  private final IncomeHistoryRepository incomeHistoryRepository;
  private final ExpenseWeightResolver expenseWeightResolver;
  private final CategoryCpiRateService categoryCpiRateService;

  @Override
  @Transactional(readOnly = true)
  public PpiResult calculate(UUID userId, String baseYearMonth) {
    if (userId == null || baseYearMonth == null || baseYearMonth.length() != 6) {
      return PpiResult.notAvailable(baseYearMonth);
    }
    YearMonth target = YearMonth.parse(baseYearMonth, YM);
    List<IncomeHistory> histories =
        incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId);
    Optional<IncomeHistory> current =
        histories.stream()
            .filter(h -> !h.getEffectiveDate().isAfter(target.atEndOfMonth()))
            .findFirst();
    if (current.isEmpty()) {
      return PpiResult.notAvailable(baseYearMonth);
    }
    String baseYm = baseMonth(histories);
    if (target.isBefore(YearMonth.parse(baseYm, YM))) {
      return PpiResult.notAvailable(baseYearMonth);
    }
    LocalDate baseEnd = YearMonth.parse(baseYm, YM).atEndOfMonth();
    IncomeHistory base =
        histories.stream()
            .filter(h -> !h.getEffectiveDate().isAfter(baseEnd))
            .findFirst()
            .orElseThrow();
    Optional<BigDecimal> incomeRatio = incomeRatio(base, current.get());
    if (incomeRatio.isEmpty()) {
      return PpiResult.notAvailable(baseYearMonth);
    }

    Optional<String> latestCpi = categoryCpiRateService.latestCpiMonth(baseYearMonth);
    if (latestCpi.isEmpty()
        || YearMonth.parse(latestCpi.get(), YM)
            .isBefore(target.minusMonths(CategoryCpiRateService.MAX_LAG_MONTHS))) {
      return delayed(baseYearMonth);
    }

    String cpiEnd = min(latestCpi.get(), baseYearMonth);
    List<String> months = monthsBetween(baseYm, baseYearMonth);
    Map<String, MonthlyWeights> weights = expenseWeightResolver.resolveMonths(userId, months);
    Optional<BigDecimal> priceIndex =
        cpiEnd.compareTo(baseYm) <= 0
            ? Optional.of(BigDecimal.ONE)
            : PersonalPriceIndexCalculator.accumulate(
                baseYm, cpiEnd, categoryCpiRateService.monthOverMonth(baseYm, cpiEnd), weights);
    if (priceIndex.isEmpty()) {
      return PpiResult.notAvailable(baseYearMonth);
    }

    BigDecimal value =
        incomeRatio
            .get()
            .divide(priceIndex.get(), MathContext.DECIMAL64)
            .multiply(HUNDRED)
            .setScale(1, RoundingMode.HALF_UP);
    if (value.signum() <= 0) {
      return PpiResult.notAvailable(baseYearMonth);
    }
    long elapsed = ChronoUnit.MONTHS.between(YearMonth.parse(baseYm, YM), target);
    IndexStatus status =
        elapsed >= FULL_HISTORY_MONTHS ? IndexStatus.OK : IndexStatus.INSUFFICIENT_HISTORY;
    return new PpiResult(
        baseYearMonth,
        value,
        status,
        rollup(weights.get(baseYearMonth)),
        baseYm,
        YearMonth.parse(min(cpiEnd, baseYearMonth), YM).atEndOfMonth(),
        asIndex(incomeRatio.get()),
        asIndex(priceIndex.get()));
  }

  /** 기준월 대비 비율을 기준월 = 100 지수로. 추이 차트의 두 선이 된다. */
  private static BigDecimal asIndex(BigDecimal ratio) {
    return ratio.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * 연봉이 처음 있는 달과 CPI 가 처음 있는 달 중 늦은 달. CPI 수집 이전 연봉을 기준으로 잡으면 그 사이 물가를 누적할 수 없어 어느 달도 값이 나오지 않는다.
   * CPI 가 하나도 없으면 첫 연봉 달이다 — 시차 검사가 UPDATE_DELAYED 로 돌려보낸다.
   */
  private String baseMonth(List<IncomeHistory> histories) {
    String firstIncome =
        YearMonths.of(
            histories.stream()
                .map(IncomeHistory::getEffectiveDate)
                .min(Comparator.naturalOrder())
                .orElseThrow());
    return categoryCpiRateService
        .firstCpiMonth()
        .map(firstCpi -> max(firstIncome, firstCpi))
        .orElse(firstIncome);
  }

  /** 기준·현재 둘 다 실수령액이 있으면 실수령액, 아니면 둘 다 세전. 한쪽만 섞으면 세율 차이가 구매력 변화로 읽힌다. */
  private static Optional<BigDecimal> incomeRatio(IncomeHistory base, IncomeHistory current) {
    boolean net = positive(base.getNetSalary()) && positive(current.getNetSalary());
    Long from = net ? EffectiveIncome.monthlyNet(base) : EffectiveIncome.annualGross(base);
    Long to = net ? EffectiveIncome.monthlyNet(current) : EffectiveIncome.annualGross(current);
    if (!positive(from) || !positive(to)) {
      return Optional.empty();
    }
    return Optional.of(
        BigDecimal.valueOf(to).divide(BigDecimal.valueOf(from), MathContext.DECIMAL64));
  }

  private static boolean positive(Long amount) {
    return amount != null && amount > 0;
  }

  private static ExpenseWeightStatus rollup(MonthlyWeights weights) {
    Set<WeightStatusEnum> statuses = EnumSet.noneOf(WeightStatusEnum.class);
    statuses.addAll(weights.statuses().values());
    if (statuses.size() != 1) {
      return ExpenseWeightStatus.PARTIALLY_ESTIMATED;
    }
    return switch (statuses.iterator().next()) {
      case USER_DEFINED -> ExpenseWeightStatus.USER_DEFINED;
      case PROPAGATED -> ExpenseWeightStatus.PROPAGATED;
      case DEFAULT_CPI -> ExpenseWeightStatus.DEFAULT_CPI;
    };
  }

  private static PpiResult delayed(String baseYearMonth) {
    return new PpiResult(
        baseYearMonth,
        null,
        IndexStatus.UPDATE_DELAYED,
        ExpenseWeightStatus.DEFAULT_CPI,
        null,
        null);
  }

  /** {@code [from, to]} 의 각 달. {@code to < from} 이면 {@code to} 하나. */
  private static List<String> monthsBetween(String from, String to) {
    List<String> months = new ArrayList<>();
    YearMonth end = YearMonth.parse(to, YM);
    for (YearMonth m = YearMonth.parse(from, YM); !m.isAfter(end); m = m.plusMonths(1)) {
      months.add(YM.format(m));
    }
    if (months.isEmpty()) {
      months.add(to);
    }
    return months;
  }

  private static String min(String a, String b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  private static String max(String a, String b) {
    return a.compareTo(b) >= 0 ? a : b;
  }
}
