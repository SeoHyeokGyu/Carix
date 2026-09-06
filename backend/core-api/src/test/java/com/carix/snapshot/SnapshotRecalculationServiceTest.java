package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.index.CurrentSalaryProvider;
import com.carix.index.MarketValueIndexProvider;
import com.carix.index.PurchasingPowerIndexProvider;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.MviResult;
import com.carix.index.dto.PpiResult;
import com.carix.index.dto.SourceCoverage;
import com.carix.index.dto.WageBaseline;
import com.carix.index.enums.ExpenseWeightStatus;
import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SnapshotRecalculationServiceTest {

  private static final String CURRENT_YM = "202607";

  @Autowired private IndexSnapshotRepository indexSnapshotRepository;

  private UUID userId;
  private StubProviders providers;
  private FixedIssuer issuer;
  private SnapshotRecalculationService service;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    providers = new StubProviders();
    issuer = new FixedIssuer();
    service =
        new SnapshotRecalculationService(
            providers,
            new StubMviProvider(),
            providers,
            new DecisionSignalEngine(),
            issuer,
            indexSnapshotRepository,
            () -> CURRENT_YM);
  }

  @Test
  @DisplayName("fromYm 부터 현재월까지 각 월에 스냅샷을 기록한다")
  void recalculate_writesRowForEachMonthInRange() {
    int written = service.recalculateSnapshot(userId, "202605");

    assertThat(written).isEqualTo(3);
    assertThat(indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId))
        .extracting(IndexSnapshot::getBaseYearMonth)
        .containsExactly("202607", "202606", "202605");
  }

  @Test
  @DisplayName("한 번의 호출은 모든 월에 같은 data_version 을 쓴다 — 발급 단위는 실행이다")
  void recalculate_usesOneDataVersionPerRun() {
    service.recalculateSnapshot(userId, "202605");

    assertThat(indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId))
        .extracting(IndexSnapshot::getDataVersion)
        .containsOnly("20260701093000-000");
  }

  @Test
  @DisplayName("과거월의 UPDATE_DELAYED 는 스킵하지 않는다 — 행을 만들고 데이터 부족으로 기록한다")
  void recalculate_writesInsufficientDataRowWhenPastMonthCpiDelayed() {
    // 스킵은 최신월(=currentYearMonth) 에만 적용된다 (기술 명세 §1.2, 기능 명세 §4.3). 202606 은
    // 202605~202607 범위의 중간월이라 스킵 대상이 아니다 — 행을 만들고 PPI 는 NULL, 신호는
    // INSUFFICIENT_DATA 로 기록해야 한다.
    providers.ppiStatusOverrides = Map.of("202606", IndexStatus.UPDATE_DELAYED);

    int written = service.recalculateSnapshot(userId, "202605");

    assertThat(written).isEqualTo(3);
    assertThat(indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId))
        .extracting(IndexSnapshot::getBaseYearMonth)
        .containsExactly("202607", "202606", "202605");

    IndexSnapshot delayedMonth =
        indexSnapshotRepository.findByUserIdAndBaseYearMonth(userId, "202606").get(0);
    assertThat(delayedMonth.getPurchasingPowerIndex()).isNull();
    assertThat(delayedMonth.getDecisionSignal()).isEqualTo(DecisionSignalCode.INSUFFICIENT_DATA);
  }

  @Test
  @DisplayName("CPI 최신월 결측(UPDATE_DELAYED)인 월은 행을 만들지 않는다 — 갭으로 남긴다")
  void recalculate_skipsLatestMonthWhenCpiDelayed() {
    providers.ppiStatusOverrides = Map.of(CURRENT_YM, IndexStatus.UPDATE_DELAYED);

    int written = service.recalculateSnapshot(userId, "202605");

    assertThat(written).isEqualTo(2);
    assertThat(indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId))
        .extracting(IndexSnapshot::getBaseYearMonth)
        .containsExactly("202606", "202605");
  }

  @Test
  @DisplayName("PPI 가 NOT_AVAILABLE(값 없음, 지연 아님)이어도 행을 만든다 — 갭은 최신월의 UPDATE_DELAYED 뿐이다")
  void recalculate_writesRowWhenPpiNotAvailable() {
    // 최신월의 UPDATE_DELAYED 만 갭이어야 한다. status 대신 value == null 로 스킵을 판단하는 회귀를 잡는
    // 케이스 — 이 케이스는 값이 없지만 지연이 아니므로 행이 있어야 한다.
    providers.ppiStatusOverrides = Map.of(CURRENT_YM, IndexStatus.NOT_AVAILABLE);

    int written = service.recalculateSnapshot(userId, CURRENT_YM);

    assertThat(written).isEqualTo(1);
    IndexSnapshot snapshot =
        indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId).get(0);
    assertThat(snapshot.getPurchasingPowerIndex()).isNull();
    assertThat(snapshot.getDecisionSignal()).isEqualTo(DecisionSignalCode.INSUFFICIENT_DATA);
  }

  @Test
  @DisplayName("PPI 가 INSUFFICIENT_HISTORY(값 100, OK 아님)면 갭이 아니라 정상 행을 만든다")
  void recalculate_writesRowWhenPpiInsufficientHistory() {
    // 값이 있으면서 OK 가 아닌 유일한 상태다 — status == UPDATE_DELAYED && 최신월 판정과 value == null
    // 판정이 갈라지는 반대 방향 케이스. 값이 있으니 스킵되면 안 된다.
    providers.ppiStatusOverrides = Map.of(CURRENT_YM, IndexStatus.INSUFFICIENT_HISTORY);

    int written = service.recalculateSnapshot(userId, CURRENT_YM);

    assertThat(written).isEqualTo(1);
    IndexSnapshot snapshot =
        indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId).get(0);
    assertThat(snapshot.getPurchasingPowerIndex()).isEqualByComparingTo("100.00");
  }

  @Test
  @DisplayName("연봉이 없으면 행을 만들되 데이터 부족으로 기록한다 — 스킵하지 않는다")
  void recalculate_writesInsufficientDataRowWhenSalaryMissing() {
    providers.salary = null;

    service.recalculateSnapshot(userId, CURRENT_YM);

    IndexSnapshot snapshot =
        indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId).get(0);
    assertThat(snapshot.getDecisionSignal()).isEqualTo(DecisionSignalCode.INSUFFICIENT_DATA);
    assertThat(snapshot.getRationale().salaryGapBucket()).isNull();
  }

  @Test
  @DisplayName("같은 버전으로 재실행하면 행이 대체되고 늘어나지 않는다")
  void recalculate_isIdempotentWhenVersionIsFixed() {
    issuer.fixed = true;

    service.recalculateSnapshot(userId, CURRENT_YM);
    service.recalculateSnapshot(userId, CURRENT_YM);

    assertThat(indexSnapshotRepository.findByUserIdAndBaseYearMonth(userId, CURRENT_YM)).hasSize(1);
  }

  @Test
  @DisplayName("rationale 에 금액을 남기지 않는다 — 격차 비율만 있고 중앙값은 없다")
  void recalculate_rationaleCarriesNoAmount() {
    service.recalculateSnapshot(userId, CURRENT_YM);

    IndexSnapshot snapshot =
        indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId).get(0);
    assertThat(snapshot.getRationale().salaryGapBucket()).isNotNull();
    assertThat(snapshot.getRationale().wageStatVintage()).isEqualTo("202606");
    assertThat(snapshot.getRationale().toString())
        .doesNotContain("72000000")
        .doesNotContain("60000000");
  }

  /**
   * PPI·연봉 provider 를 한 객체로 구현한 테스트용 자리표.
   *
   * <p>MVI 는 별도 클래스({@link StubMviProvider})다 — {@link PurchasingPowerIndexProvider} 와 {@link
   * MarketValueIndexProvider} 가 똑같은 시그니처 {@code calculate(UUID, String)} 를 반환형만 다르게 선언해서, 한 클래스가 둘을
   * 동시에 구현할 수 없다(JLS 8.4.8.3 — 반환형만 다른 메서드는 오버로드가 아니라 충돌이다).
   */
  private static final class StubProviders
      implements PurchasingPowerIndexProvider, CurrentSalaryProvider {

    /** 월별 PPI 상태 override. 기본은 모든 월이 OK 다 — {@code delayedMonths} 를 일반화한 자리다. */
    private Map<String, IndexStatus> ppiStatusOverrides = Map.of();

    private CurrentSalary salary = new CurrentSalary(60_000_000L, LocalDate.of(2026, 1, 1));

    @Override
    public PpiResult calculate(UUID userId, String baseYearMonth) {
      IndexStatus status = ppiStatusOverrides.getOrDefault(baseYearMonth, IndexStatus.OK);
      return switch (status) {
        case UPDATE_DELAYED ->
            new PpiResult(
                baseYearMonth,
                null,
                IndexStatus.UPDATE_DELAYED,
                ExpenseWeightStatus.USER_DEFINED,
                null,
                null);
        case NOT_AVAILABLE -> PpiResult.notAvailable(baseYearMonth);
        case INSUFFICIENT_HISTORY ->
            new PpiResult(
                baseYearMonth,
                new BigDecimal("100.00"),
                IndexStatus.INSUFFICIENT_HISTORY,
                ExpenseWeightStatus.USER_DEFINED,
                baseYearMonth,
                LocalDate.of(2026, 7, 1));
        default ->
            new PpiResult(
                baseYearMonth,
                new BigDecimal("94.20"),
                IndexStatus.OK,
                ExpenseWeightStatus.USER_DEFINED,
                "202601",
                LocalDate.of(2026, 7, 1));
      };
    }

    @Override
    public Optional<CurrentSalary> currentGrossSalary(UUID userId, String baseYearMonth) {
      return Optional.ofNullable(salary);
    }
  }

  /** MVI provider 자리표. 어느 테스트도 MVI 결과를 바꾸지 않아 상태 없이 고정값만 돌려준다. */
  private static final class StubMviProvider implements MarketValueIndexProvider {

    @Override
    public MviResult calculate(UUID userId, String baseYearMonth) {
      return new MviResult(
          baseYearMonth,
          new BigDecimal("117.50"),
          IndexStatus.OK,
          List.of(new SourceCoverage("SARAMIN", 1, 40, true, "서울 전체", new BigDecimal("42.5"))),
          List.of(),
          List.of(),
          List.of(),
          new WageBaseline(72_000_000L, "초과급여 제외, 상여 포함", "202606"),
          LocalDate.of(2026, 7, 1));
    }
  }

  /** 고정 발급기. 이게 없으면 매 실행이 새 버전을 받아 대체 경로가 한 번도 실행되지 않는다. */
  private static final class FixedIssuer implements DataVersionIssuer {
    private boolean fixed = false;
    private int sequence = 0;

    @Override
    public String issue(UUID userId) {
      return fixed ? "20260701093000-000" : "20260701093000-%03d".formatted(sequence++);
    }
  }
}
