package com.carix.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.expense.domain.ExpenseHistory;
import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.dto.PpiResult;
import com.carix.index.enums.ExpenseWeightStatus;
import com.carix.index.enums.IndexStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultPurchasingPowerIndexProviderTest {

  private IncomeHistoryRepository incomeHistoryRepository;
  private ExpenseHistoryRepository expenseHistoryRepository;
  private DefaultPurchasingPowerIndexProvider provider;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    incomeHistoryRepository = mock(IncomeHistoryRepository.class);
    expenseHistoryRepository = mock(ExpenseHistoryRepository.class);
    provider =
        new DefaultPurchasingPowerIndexProvider(incomeHistoryRepository, expenseHistoryRepository);
  }

  @Test
  @DisplayName("userId 또는 baseYearMonth가 유효하지 않으면 NOT_AVAILABLE")
  void invalidInput_returnsNotAvailable() {
    assertThat(provider.calculate(null, "202607").status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
    assertThat(provider.calculate(userId, null).status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
    assertThat(provider.calculate(userId, "2026-07").status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
  }

  @Test
  @DisplayName("소득 이력이 없으면 NOT_AVAILABLE 반환")
  void noIncomeHistory_returnsNotAvailable() {
    given(incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId))
        .willReturn(Collections.emptyList());

    PpiResult result = provider.calculate(userId, "202607");
    assertThat(result.status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
    assertThat(result.value()).isNull();
  }

  @Test
  @DisplayName("소득 이력이 1건인 초기 상태에서는 기준월 100.0 및 INSUFFICIENT_HISTORY 반환")
  void singleIncomeHistory_returnsBase100AndInsufficientHistory() {
    IncomeHistory income =
        new IncomeHistory(userId, 60_000_000L, 48_000_000L, LocalDate.of(2026, 7, 1), null);

    given(incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId))
        .willReturn(List.of(income));
    given(expenseHistoryRepository.findByUserIdOrderByBaseYearMonthDesc(userId))
        .willReturn(Collections.emptyList());

    PpiResult result = provider.calculate(userId, "202607");

    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_HISTORY);
    assertThat(result.value()).isEqualTo(new BigDecimal("100.0"));
    assertThat(result.weightStatus()).isEqualTo(ExpenseWeightStatus.DEFAULT_CPI);
    assertThat(result.indexBaseYearMonth()).isEqualTo("202607");
  }

  @Test
  @DisplayName("소비 내역이 등록되어 있으면 USER_DEFINED 반환")
  void hasExpenseHistory_returnsUserDefined() {
    IncomeHistory income =
        new IncomeHistory(userId, 60_000_000L, 48_000_000L, LocalDate.of(2026, 7, 1), null);
    ExpenseHistory expense = new ExpenseHistory(userId, "202607");

    given(incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId))
        .willReturn(List.of(income));
    given(expenseHistoryRepository.findByUserIdOrderByBaseYearMonthDesc(userId))
        .willReturn(List.of(expense));

    PpiResult result = provider.calculate(userId, "202607");

    assertThat(result.weightStatus()).isEqualTo(ExpenseWeightStatus.USER_DEFINED);
  }

  @Test
  @DisplayName("12개월 이상 경과 후 연봉 상승 시 변동 지수 및 OK 상태 반환")
  void salaryIncreasedOver12Months_returnsCalculatedPpiAndOk() {
    IncomeHistory base =
        new IncomeHistory(userId, 50_000_000L, 40_000_000L, LocalDate.of(2025, 1, 1), null);
    IncomeHistory current =
        new IncomeHistory(userId, 60_000_000L, 48_000_000L, LocalDate.of(2026, 7, 1), null);

    given(incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId))
        .willReturn(List.of(current, base));
    given(expenseHistoryRepository.findByUserIdOrderByBaseYearMonthDesc(userId))
        .willReturn(Collections.emptyList());

    PpiResult result = provider.calculate(userId, "202607");

    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    // (60,000,000 / 50,000,000) * 100.0 = 120.0
    assertThat(result.value()).isEqualTo(new BigDecimal("120.0"));
    assertThat(result.indexBaseYearMonth()).isEqualTo("202501");
  }
}
