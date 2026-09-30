package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.common.EncryptedConverter;
import com.carix.common.KeyRing;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.income.service.IncomeCurrentSalaryProvider;
import com.carix.index.MarketValueIndexProvider;
import com.carix.index.PurchasingPowerIndexProvider;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.MviResult;
import com.carix.index.dto.PpiResult;
import com.carix.index.dto.SourceCoverage;
import com.carix.index.dto.WageBaseline;
import com.carix.index.enums.ExpenseWeightStatus;
import com.carix.index.enums.IndexStatus;
import com.carix.index.stub.StubOpeningWindowIndexProvider;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.domain.SnapshotRationale;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 스냅샷 복호화 경계 검증 테스트 (SECURITY §3.2, 기능 명세 §7 완료 기준 10).
 *
 * <p>검증 항목:
 *
 * <ul>
 *   <li>1. 소득 도메인(IncomeCurrentSalaryProvider)을 통해 AES-256-GCM 복호화가 안전하게 수행되는가
 *   <li>2. 스냅샷 테이블(core.index_snapshot) 및 rationale(JSONB)에 연봉 평문 금액이 절대 저장되지 않는가
 *   <li>3. 소득 격차는 정확한 수치가 아닌 SalaryGapBucket 구간으로만 저장되는가
 *   <li>4. CurrentSalary DTO의 toString()이 금액을 마스킹하여 로그 누출을 방지하는가
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IncomeCurrentSalaryProvider.class, KeyRing.class, EncryptedConverter.class})
class SnapshotDecryptionBoundarySecurityTest {

  private static final String BASE_YM = "202607";
  private static final long SENSITIVE_SALARY = 75_000_000L;

  @Autowired private IncomeHistoryRepository incomeHistoryRepository;
  @Autowired private IndexSnapshotRepository indexSnapshotRepository;
  @Autowired private IncomeCurrentSalaryProvider salaryProvider;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID userId;
  private SnapshotRecalculationService recalculationService;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // Member A 지수 스텁 (지수 결과에는 연봉이 없음)
    PurchasingPowerIndexProvider ppiProvider =
        (uid, ym) ->
            new PpiResult(
                ym,
                new BigDecimal("102.50"),
                IndexStatus.OK,
                ExpenseWeightStatus.DEFAULT_CPI,
                ym,
                LocalDate.of(2026, 7, 1));

    // Member B 지수 스텁 (임금통계 중앙값 72,000,000 설정)
    MarketValueIndexProvider mviProvider =
        (uid, ym) ->
            new MviResult(
                ym,
                new BigDecimal("117.50"),
                IndexStatus.OK,
                List.of(
                    new SourceCoverage("SARAMIN", 1, 40, true, "서울 전체", new BigDecimal("42.5"))),
                List.of(),
                List.of(),
                List.of(),
                new WageBaseline(72_000_000L, "초과급여 제외, 상여 포함", "202606"),
                LocalDate.of(2026, 7, 1));

    DecisionSignalEngine decisionSignalEngine = new DecisionSignalEngine();
    DataVersionIssuer issuer = u -> "data-ver-test";

    recalculationService =
        new SnapshotRecalculationService(
            ppiProvider,
            mviProvider,
            new StubOpeningWindowIndexProvider(),
            salaryProvider,
            decisionSignalEngine,
            issuer,
            indexSnapshotRepository,
            (u, ym) -> Optional.empty(),
            () -> BASE_YM);
  }

  @Test
  @DisplayName("스냅샷 복호화 경계: 복호화된 연봉은 스냅샷 저장소 및 rationale JSON에 평문으로 남지 않는다")
  void decryptionBoundary_doesNotLeakPlaintextSalaryToStorage() throws Exception {
    // 1. 소득 이력 저장 (JPA Converter를 통해 AES-256-GCM 암호화되어 저장됨)
    IncomeHistory income =
        new IncomeHistory(userId, SENSITIVE_SALARY, 60_000_000L, LocalDate.of(2026, 1, 1), null);
    incomeHistoryRepository.saveAndFlush(income);

    // DB 원천 컬럼 확인: 평문이 아님
    String rawGrossInDb =
        jdbcTemplate.queryForObject(
            "SELECT gross_salary_encrypted FROM core.income_history WHERE user_id = ?",
            String.class,
            userId);
    assertThat(rawGrossInDb).doesNotContain(String.valueOf(SENSITIVE_SALARY));
    assertThat(rawGrossInDb).startsWith("v1:");

    // 2. Member A 복호화 경계 확인: CurrentSalaryProvider 가 정상 복호화 DTO 반환
    Optional<CurrentSalary> currentSalaryOpt = salaryProvider.currentGrossSalary(userId, BASE_YM);
    assertThat(currentSalaryOpt).isPresent();
    assertThat(currentSalaryOpt.get().grossSalary()).isEqualTo(SENSITIVE_SALARY);

    // 3. CurrentSalary.toString() 로깅 마스킹 검증 (완료 기준 10)
    String salaryString = currentSalaryOpt.get().toString();
    assertThat(salaryString).contains("grossSalary=***");
    assertThat(salaryString).doesNotContain(String.valueOf(SENSITIVE_SALARY));

    // 4. 스냅샷 재계산 실행
    int written = recalculationService.recalculateSnapshot(userId, BASE_YM);
    assertThat(written).isEqualTo(1);

    // 5. 저장된 스냅샷 조회
    List<IndexSnapshot> snapshots =
        indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId);
    assertThat(snapshots).hasSize(1);
    IndexSnapshot snapshot = snapshots.get(0);

    // 지수 컬럼 검증
    assertThat(snapshot.getPurchasingPowerIndex()).isNotNull();
    assertThat(snapshot.getMarketValueIndex()).isNotNull();

    // 6. DB Native Query로 스냅샷 행 전체 덤프 검증
    String rationaleJsonRaw =
        jdbcTemplate.queryForObject(
            "SELECT rationale FROM core.index_snapshot WHERE user_id = ?", String.class, userId);

    // Rationale JSON 전체에 연봉 평문 문자열이 전혀 없음을 단언
    assertThat(rationaleJsonRaw)
        .isNotNull()
        .doesNotContain(String.valueOf(SENSITIVE_SALARY))
        .doesNotContain("75000000");

    // 7. SnapshotRationale 객체 검증
    SnapshotRationale rationale = snapshot.getRationale();
    assertThat(rationale).isNotNull();

    // 소득 격차 비율이 아니라 구간 이름(SalaryGapBucket)만 저장되었는지 확인
    assertThat(rationale.salaryGapBucket()).isNotNull();
    assertThat(SalaryGapBucket.valueOf(rationale.salaryGapBucket())).isNotNull();

    // Jackson 직렬화 시에도 연봉 숫자가 들어가지 않음 확인
    String serializedRationale = objectMapper.writeValueAsString(rationale);
    assertThat(serializedRationale).doesNotContain(String.valueOf(SENSITIVE_SALARY));
  }
}
