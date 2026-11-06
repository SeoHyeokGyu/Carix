package com.carix.income.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.carix.income.domain.IncomeChangeReasonEnum;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.RecordedIncomeChange;
import com.carix.index.dto.RecordedYearIncome;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
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

  private IncomeHistory row(long gross, LocalDate effectiveDate) {
    return new IncomeHistory(userId, gross, gross / 2, effectiveDate, null);
  }

  /** repository 는 effective_date DESC 로 준다. */
  private List<RecordedYearIncome> recorded(IncomeHistory... rowsDesc) {
    given(repository.findByUserIdOrderByEffectiveDateDesc(userId)).willReturn(List.of(rowsDesc));
    return provider.recordedYearIncomes(userId);
  }

  @Test
  @DisplayName("소득 행은 유효일 오름차순으로 내고 세전 0 이하 행은 없는 행으로 본다")
  void recordedIncomeChanges_ascendingWithoutZeroRows() {
    given(repository.findByUserIdOrderByEffectiveDateDesc(userId))
        .willReturn(
            List.of(
                new IncomeHistory(
                    userId,
                    69_000_000L,
                    4_000_000L,
                    LocalDate.of(2026, 2, 1),
                    IncomeChangeReasonEnum.JOB_CHANGE),
                row(0L, LocalDate.of(2025, 9, 1)),
                row(55_000_000L, LocalDate.of(2025, 7, 1))));

    assertThat(provider.recordedIncomeChanges(userId))
        .containsExactly(
            new RecordedIncomeChange(LocalDate.of(2025, 7, 1), 55_000_000L, null),
            new RecordedIncomeChange(
                LocalDate.of(2026, 2, 1), 69_000_000L, IncomeChangeReasonEnum.JOB_CHANGE));
  }

  @Test
  @DisplayName("한 해에 행이 여럿이면 마지막 유효일 행이 그해 값이고 연도 오름차순이다")
  void lastRowOfYearWins() {
    assertThat(
            recorded(
                row(50_000_000L, LocalDate.of(2024, 1, 1)),
                row(42_000_000L, LocalDate.of(2023, 11, 1)),
                row(40_000_000L, LocalDate.of(2023, 3, 1))))
        .containsExactly(
            new RecordedYearIncome(2023, 42_000_000L), new RecordedYearIncome(2024, 50_000_000L));
  }

  @Test
  @DisplayName("그해 마지막 행이 0원이면 앞선 양수 행을 쓴다 — 0원을 그해 값으로 쓰면 -100% 가 나온다")
  void zeroRowFallsBackToEarlierRowInYear() {
    assertThat(
            recorded(
                row(0L, LocalDate.of(2023, 11, 1)), row(40_000_000L, LocalDate.of(2023, 3, 1))))
        .containsExactly(new RecordedYearIncome(2023, 40_000_000L));
  }

  @Test
  @DisplayName("전부 0원인 해는 기록 연도가 아니다")
  void yearWithOnlyZeroRowsIsSkipped() {
    assertThat(
            recorded(row(40_000_000L, LocalDate.of(2023, 1, 1)), row(0L, LocalDate.of(2022, 1, 1))))
        .containsExactly(new RecordedYearIncome(2023, 40_000_000L));
  }

  @Test
  @DisplayName("따로 받는 고정상여를 더한다 — 시장 중앙값이 상여를 포함한다")
  void addsSeparateFixedBonus() {
    IncomeHistory separate = row(40_000_000L, LocalDate.of(2023, 1, 1));
    separate.applyBonusTerms(false, 5_000_000L);

    assertThat(recorded(separate)).containsExactly(new RecordedYearIncome(2023, 45_000_000L));
  }

  @Test
  @DisplayName("총액이 같으면 상여 조건이 달라도 값이 같다 — 조건 표기 차이로 가짜 인상이 나오면 안 된다")
  void sameTotalIsSameValueRegardlessOfBonusTerms() {
    IncomeHistory separate = row(40_000_000L, LocalDate.of(2023, 1, 1));
    separate.applyBonusTerms(false, 5_000_000L);

    assertThat(recorded(separate, row(45_000_000L, LocalDate.of(2022, 1, 1))))
        .extracting(RecordedYearIncome::annualGross)
        .containsExactly(45_000_000L, 45_000_000L);
  }

  @Test
  @DisplayName("연도별 기록 조회가 금액을 로그에 남기지 않는다 (완료 기준 10)")
  void doesNotLogAmounts(CapturedOutput output) {
    Logger logger = (Logger) LoggerFactory.getLogger(IncomeCurrentSalaryProvider.class);
    Level previous = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    try {
      recorded(row(40_000_000L, LocalDate.of(2023, 1, 1)));
    } finally {
      logger.setLevel(previous);
    }

    // 감사 문구가 찍혀야 캡처가 동작한다는 증거가 된다 — 없으면 아래 단언은 공허하다
    assertThat(output).contains(userId.toString());
    assertThat(output).doesNotContain("40000000");
  }

  @Test
  @DisplayName("RecordedYearIncome 의 toString 은 금액을 드러내지 않는다 (완료 기준 10)")
  void toStringMasksAmount() {
    assertThat(new RecordedYearIncome(2023, 40_000_000L).toString())
        .contains("2023")
        .doesNotContain("40000000");
  }
}
