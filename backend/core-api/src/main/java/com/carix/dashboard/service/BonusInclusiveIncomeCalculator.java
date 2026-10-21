package com.carix.dashboard.service;

import com.carix.dashboard.dto.DashboardSummaryResponse.BonusInclusiveIncome;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.dto.PerformanceBonusResponse;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.income.service.PerformanceBonusService;
import com.carix.index.EffectiveIncome;
import com.carix.index.inflation.InflationComparison;
import com.carix.index.inflation.PersonalInflationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 성과급을 포함한 최근 12개월 소득을 직전 12개월과 견주고 개인물가로 디플레이트한다. 대시보드 별도 줄 전용이다 — 성과급은 구매력 지수에 섞지 않는다.
 *
 * <p>한 달의 소득은 그 달 말일 기준 유효한 소득 행의 연 소득/12 이다 (고정상여는 {@link EffectiveIncome} 이 연액에 포함). 성과급은 지급월에
 * 그대로 더한다.
 */
@Component
@RequiredArgsConstructor
public class BonusInclusiveIncomeCalculator {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final int PERIOD_MONTHS = 12;

  private final IncomeHistoryRepository incomeHistoryRepository;
  private final PerformanceBonusService performanceBonusService;
  private final PersonalInflationService personalInflationService;

  /** 성과급 기록이 하나도 없으면 {@code null}. */
  public BonusInclusiveIncome calculate(UUID userId, String baseYearMonth) {
    List<PerformanceBonusResponse> bonuses = performanceBonusService.list(userId);
    if (bonuses.isEmpty()) {
      return null;
    }
    YearMonth base = YearMonth.parse(baseYearMonth, YM);
    List<IncomeHistory> incomes =
        incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId);

    Period recent = period(incomes, bonuses, base);
    Period previous = period(incomes, bonuses, base.minusMonths(PERIOD_MONTHS));
    return new BonusInclusiveIncome(
        recent.total(),
        previous.total(),
        realChangePercent(userId, baseYearMonth, recent, previous),
        recent.bonusCount());
  }

  private Double realChangePercent(
      UUID userId, String baseYearMonth, Period recent, Period previous) {
    if (!previous.hasIncomeRow() || previous.total() <= 0) {
      return null;
    }
    Optional<InflationComparison> inflation =
        personalInflationService.trailing12m(userId, baseYearMonth);
    if (inflation.isEmpty()) {
      return null;
    }
    BigDecimal nominal =
        BigDecimal.valueOf(recent.total())
            .divide(BigDecimal.valueOf(previous.total()), 10, RoundingMode.HALF_UP);
    BigDecimal deflator = BigDecimal.ONE.add(inflation.get().personalRate().movePointLeft(2));
    return nominal
        .divide(deflator, 10, RoundingMode.HALF_UP)
        .subtract(BigDecimal.ONE)
        .movePointRight(2)
        .setScale(1, RoundingMode.HALF_UP)
        .doubleValue();
  }

  /** {@code end} 를 마지막 달로 하는 12개월. */
  private static Period period(
      List<IncomeHistory> incomes, List<PerformanceBonusResponse> bonuses, YearMonth end) {
    YearMonth start = end.minusMonths(PERIOD_MONTHS - 1);
    long grossSum = 0;
    boolean hasIncomeRow = false;
    for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
      Optional<IncomeHistory> row = effectiveRow(incomes, month.atEndOfMonth());
      if (row.isPresent()) {
        hasIncomeRow = true;
        grossSum += EffectiveIncome.annualGross(row.get());
      }
    }
    int bonusCount = 0;
    long bonusSum = 0;
    for (PerformanceBonusResponse bonus : bonuses) {
      YearMonth paid = YearMonth.parse(bonus.paidMonth(), YM);
      if (!paid.isBefore(start) && !paid.isAfter(end)) {
        bonusCount++;
        bonusSum += bonus.amount();
      }
    }
    return new Period(
        Math.round(grossSum / (double) PERIOD_MONTHS) + bonusSum, bonusCount, hasIncomeRow);
  }

  /** 입력은 효력일 내림차순이다. */
  private static Optional<IncomeHistory> effectiveRow(List<IncomeHistory> incomes, LocalDate asOf) {
    return incomes.stream().filter(row -> !row.getEffectiveDate().isAfter(asOf)).findFirst();
  }

  private record Period(long total, int bonusCount, boolean hasIncomeRow) {}
}
