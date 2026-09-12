package com.carix.index;

import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.dto.PpiResult;
import com.carix.index.enums.ExpenseWeightStatus;
import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.YearMonths;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매력 지수(PPI) 기본 산출기.
 *
 * <p>Member A의 지출목적별 CPI 산출 엔진 배포 전까지, 사용자가 등록한 소득 이력({@code core.income_history})과 소비 이력({@code
 * core.expense_history})을 바탕으로 기준월(=100.0) 대비 구매력 지수를 도출합니다.
 *
 * <ul>
 *   <li>소득 이력이 전혀 없는 경우: {@link PpiResult#notAvailable(String)}
 *   <li>소득 이력이 존재하는 경우: 최초 소득월을 지수 기준월({@code indexBaseYearMonth})로 삼고, 소득 변동 비율에 따라 지수를 산출합니다.
 *   <li>기준월이거나 이력이 12개월 미만인 경우: {@link IndexStatus#INSUFFICIENT_HISTORY} 상태와 함께 지수 100.0을 보존합니다.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPurchasingPowerIndexProvider implements PurchasingPowerIndexProvider {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final BigDecimal BASE_INDEX = new BigDecimal("100.0");

  private final IncomeHistoryRepository incomeHistoryRepository;
  private final ExpenseHistoryRepository expenseHistoryRepository;

  @Override
  @Transactional(readOnly = true)
  public PpiResult calculate(UUID userId, String baseYearMonth) {
    if (userId == null || baseYearMonth == null || baseYearMonth.length() != 6) {
      return PpiResult.notAvailable(baseYearMonth);
    }

    List<IncomeHistory> histories =
        incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId);

    if (histories.isEmpty()) {
      return PpiResult.notAvailable(baseYearMonth);
    }

    YearMonth targetYm = YearMonth.parse(baseYearMonth, YM);
    LocalDate endOfMonth = targetYm.atEndOfMonth();

    // 기준월 시점 이전의 유효한 최근 연봉
    Optional<IncomeHistory> currentOpt =
        histories.stream().filter(h -> !h.getEffectiveDate().isAfter(endOfMonth)).findFirst();

    if (currentOpt.isEmpty()
        || currentOpt.get().getGrossSalary() == null
        || currentOpt.get().getGrossSalary() <= 0) {
      return PpiResult.notAvailable(baseYearMonth);
    }

    IncomeHistory currentIncome = currentOpt.get();

    // 사용자의 가장 오래된(최초) 소득 이력을 기준월로 설정
    IncomeHistory baseIncome =
        histories.stream()
            .min(Comparator.comparing(IncomeHistory::getEffectiveDate))
            .orElse(currentIncome);

    String indexBaseYm = YearMonths.of(baseIncome.getEffectiveDate());
    YearMonth baseYm = YearMonth.parse(indexBaseYm, YM);

    // 소비 가중치 출처 확인 (해당 사용자에게 등록된 소비 내역이 있으면 USER_DEFINED, 없으면 DEFAULT_CPI)
    boolean hasCustomExpense =
        !expenseHistoryRepository.findByUserIdOrderByBaseYearMonthDesc(userId).isEmpty();
    ExpenseWeightStatus weightStatus =
        hasCustomExpense ? ExpenseWeightStatus.USER_DEFINED : ExpenseWeightStatus.DEFAULT_CPI;

    long monthsBetween = ChronoUnit.MONTHS.between(baseYm, targetYm);
    IndexStatus status = monthsBetween >= 12 ? IndexStatus.OK : IndexStatus.INSUFFICIENT_HISTORY;

    BigDecimal ppiValue;
    if (baseIncome.getGrossSalary() == null
        || baseIncome.getGrossSalary() <= 0
        || baseYm.equals(targetYm)) {
      ppiValue = BASE_INDEX;
    } else {
      // (현재 세전 연봉 / 기준 세전 연봉) * 100.0
      double ratio =
          ((double) currentIncome.getGrossSalary() / (double) baseIncome.getGrossSalary()) * 100.0;
      ppiValue = BigDecimal.valueOf(ratio).setScale(1, RoundingMode.HALF_UP);
    }

    return new PpiResult(
        baseYearMonth, ppiValue, status, weightStatus, indexBaseYm, LocalDate.now());
  }
}
