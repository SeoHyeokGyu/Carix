package com.carix.income.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.dto.CurrentSalary;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IncomeCurrentSalaryProviderTest {

  private IncomeHistoryRepository repository;
  private IncomeCurrentSalaryProvider provider;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repository = mock(IncomeHistoryRepository.class);
    provider = new IncomeCurrentSalaryProvider(repository);
  }

  @Test
  @DisplayName("userId 또는 baseYearMonth가 유효하지 않으면 empty 반환")
  void invalidInput_returnsEmpty() {
    assertThat(provider.currentGrossSalary(null, "202607")).isEmpty();
    assertThat(provider.currentGrossSalary(userId, null)).isEmpty();
    assertThat(provider.currentGrossSalary(userId, "2026-07")).isEmpty();
    assertThat(provider.currentGrossSalary(userId, "2026")).isEmpty();
  }

  @Test
  @DisplayName("연봉 이력이 없으면 empty 반환")
  void noHistory_returnsEmpty() {
    given(repository.findByUserIdOrderByEffectiveDateDesc(userId))
        .willReturn(Collections.emptyList());

    assertThat(provider.currentGrossSalary(userId, "202607")).isEmpty();
  }

  @Test
  @DisplayName("기준월 말일 이전의 가장 최근 연봉을 반환한다")
  void returnsMostRecentSalaryBeforeEndOfMonth() {
    IncomeHistory recent =
        new IncomeHistory(userId, 60_000_000L, 48_000_000L, LocalDate.of(2026, 6, 1), null);
    IncomeHistory old =
        new IncomeHistory(userId, 50_000_000L, 40_000_000L, LocalDate.of(2025, 1, 1), null);
    IncomeHistory future =
        new IncomeHistory(userId, 70_000_000L, 56_000_000L, LocalDate.of(2026, 8, 1), null);

    // repository 는 effective_date DESC 정렬
    given(repository.findByUserIdOrderByEffectiveDateDesc(userId))
        .willReturn(List.of(future, recent, old));

    Optional<CurrentSalary> result = provider.currentGrossSalary(userId, "202607");

    assertThat(result).isPresent();
    assertThat(result.get().grossSalary()).isEqualTo(60_000_000L);
    assertThat(result.get().effectiveDate()).isEqualTo(LocalDate.of(2026, 6, 1));
  }

  @Test
  @DisplayName("모든 연봉 이력이 기준월보다 미래이면 empty 반환")
  void allHistoriesInFuture_returnsEmpty() {
    IncomeHistory future =
        new IncomeHistory(userId, 60_000_000L, 48_000_000L, LocalDate.of(2026, 8, 1), null);

    given(repository.findByUserIdOrderByEffectiveDateDesc(userId)).willReturn(List.of(future));

    assertThat(provider.currentGrossSalary(userId, "202607")).isEmpty();
  }

  @Test
  @DisplayName("연봉이 0 이하이면 empty 반환 (분모 0 방지)")
  void zeroOrNegativeSalary_returnsEmpty() {
    IncomeHistory zero = new IncomeHistory(userId, 0L, 0L, LocalDate.of(2026, 1, 1), null);

    given(repository.findByUserIdOrderByEffectiveDateDesc(userId)).willReturn(List.of(zero));

    assertThat(provider.currentGrossSalary(userId, "202607")).isEmpty();
  }
}
