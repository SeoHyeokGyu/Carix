package com.carix.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.domain.ExpenseHistory;
import com.carix.expense.domain.ExpenseItemEntity;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.domain.PerformanceBonus;
import com.carix.scenario.domain.CareerScenario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * SensitiveDataRekeyingService 통합 테스트.
 *
 * <p>검증 항목:
 *
 * <ul>
 *   <li>1. 접두사가 없는 레거시 암호문이 저장된 테이블(소득, 소비, 시나리오 3종)을 감지하는 {@code checkStatus()} 검증
 *   <li>2. {@code rekeyAll()} 실행 후 모든 암호문이 최신 활성 키 접두사({@code v1:})로 갱신되는지 검증
 *   <li>3. 재암호화 완료 후 엔티티를 조회했을 때 평문 금액이 정상 복호화되는지 정합성 확인
 *   <li>4. 재암호화 완료 후 {@code isFullyMigrated() == true} 및 {@code totalOutdated() == 0} 검증
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SensitiveDataRekeyingService.class, KeyRing.class, EncryptedConverter.class})
class SensitiveDataRekeyingServiceTest {

  @Autowired private SensitiveDataRekeyingService rekeyingService;
  @MockitoSpyBean private EncryptedConverter converter;
  @Autowired private KeyRing keyRing;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("구버전(또는 레거시 미접두) 암호문이 존재할 때 checkStatus와 rekeyAll로 안전하게 재암호화된다")
  void rekeyAll_migratesLegacyAndOutdatedCiphertexts() {
    UUID userId = UUID.randomUUID();

    // 1. income_history 에 레거시 암호문 (접두사 없이) 직접 INSERT
    // build.gradle 의 테스트 키(v1)로 암호화하되 접두사를 제거해 마이그레이션 대상 데이터 모사
    String v1Encrypted = converter.convertToDatabaseColumn(65_000_000L);
    String rawLegacyCiphertext =
        v1Encrypted.contains(":")
            ? v1Encrypted.substring(v1Encrypted.indexOf(':') + 1)
            : v1Encrypted;

    UUID incomeId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO core.income_history (id, user_id, gross_salary_encrypted, net_salary_encrypted, effective_date) "
            + "VALUES (?, ?, ?, ?, ?)",
        incomeId,
        userId,
        rawLegacyCiphertext,
        rawLegacyCiphertext,
        LocalDate.of(2026, 6, 1));

    // 2. expense_item 에 레거시 암호문 INSERT
    ExpenseHistory expenseHistory = new ExpenseHistory(userId, "202606");
    em.persistAndFlush(expenseHistory);
    UUID expenseItemId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO core.expense_item (id, expense_history_id, category, amount_encrypted) "
            + "VALUES (?, ?, ?, ?)",
        expenseItemId,
        expenseHistory.getId(),
        ExpenseCategoryEnum.FOOD.name(),
        rawLegacyCiphertext);

    // 3. career_scenario 에 레거시 암호문 INSERT
    UUID scenarioId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO core.career_scenario (id, user_id, scenario_name, current_gross_salary_encrypted, "
            + "stay_expected_raise_rate, move_target_gross_salary_encrypted, move_one_time_cost, "
            + "expected_unemployment_months, is_eligible_for_unemployment_benefit, stay_compensation_12m_encrypted, "
            + "move_compensation_12m_encrypted, estimated_unemployment_benefit_encrypted, difference_encrypted, "
            + "feasibility_status, calculated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        scenarioId,
        userId,
        "시나리오 1",
        rawLegacyCiphertext,
        new BigDecimal("5.00"),
        rawLegacyCiphertext,
        0L,
        0,
        false,
        rawLegacyCiphertext,
        rawLegacyCiphertext,
        rawLegacyCiphertext,
        rawLegacyCiphertext,
        "OK",
        OffsetDateTime.now());

    em.clear();

    // 4. checkStatus 확인: 3개 테이블 모두 미전환(접두사 없음) 상태 감지
    SensitiveDataRekeyingService.RekeyingStatus statusBefore = rekeyingService.checkStatus();
    assertThat(statusBefore.isFullyMigrated()).isFalse();
    assertThat(statusBefore.totalOutdated()).isGreaterThanOrEqualTo(3);
    assertThat(statusBefore.outdatedIncomeHistory()).isGreaterThanOrEqualTo(1);
    assertThat(statusBefore.outdatedExpenseItem()).isGreaterThanOrEqualTo(1);
    assertThat(statusBefore.outdatedCareerScenario()).isGreaterThanOrEqualTo(1);

    // 5. rekeyAll 실행: 모든 대상 행을 최신 활성 키(v1:)로 재암호화
    SensitiveDataRekeyingService.RekeyingResult result = rekeyingService.rekeyAll();
    assertThat(result.totalUpdated()).isGreaterThanOrEqualTo(3);
    assertThat(result.incomeHistoryUpdated()).isGreaterThanOrEqualTo(1);
    assertThat(result.expenseItemUpdated()).isGreaterThanOrEqualTo(1);
    assertThat(result.careerScenarioUpdated()).isGreaterThanOrEqualTo(1);

    em.clear();

    // 6. checkStatus 재확인: 모든 데이터가 활성 키 접두사(v1:)로 전환 완료됨
    SensitiveDataRekeyingService.RekeyingStatus statusAfter = rekeyingService.checkStatus();
    assertThat(statusAfter.isFullyMigrated()).isTrue();
    assertThat(statusAfter.totalOutdated()).isZero();

    // 7. 엔티티를 조회했을 때 정상 복호화 확인 (데이터 무결성 검증)
    IncomeHistory reloadedIncome = em.find(IncomeHistory.class, incomeId);
    assertThat(reloadedIncome.getGrossSalary()).isEqualTo(65_000_000L);

    ExpenseItemEntity reloadedExpense = em.find(ExpenseItemEntity.class, expenseItemId);
    assertThat(reloadedExpense.getAmount()).isEqualTo(65_000_000L);

    CareerScenario reloadedScenario = em.find(CareerScenario.class, scenarioId);
    assertThat(reloadedScenario.getCurrentGrossSalary()).isEqualTo(65_000_000L);

    // 8. DB의 실제 컬럼 값이 activeKeyId 접두사를 포함하고 있는지 확인
    String updatedEncrypted =
        jdbcTemplate.queryForObject(
            "SELECT gross_salary_encrypted FROM core.income_history WHERE id = ?",
            String.class,
            incomeId);
    assertThat(updatedEncrypted).startsWith(keyRing.getActiveKeyId() + ":");
  }

  /**
   * 재암호화는 서비스가 요청을 받는 동안 돈다 — 읽은 뒤 쓰기 전에 사용자가 같은 행을 고칠 수 있다. 읽은 암호문이 그대로일 때만 써야 사용자의 새 값이 옛 값으로 덮이지
   * 않는다.
   */
  @Test
  @DisplayName("재암호화 도중 사용자가 같은 행을 고치면 사용자 값이 남는다 — 옛 값으로 덮어쓰지 않는다")
  void rekey_doesNotOverwriteConcurrentUserWrite() {
    String legacy = legacyCiphertext(65_000_000L);
    UUID incomeId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO core.income_history (id, user_id, gross_salary_encrypted, effective_date) "
            + "VALUES (?, ?, ?, ?)",
        incomeId,
        UUID.randomUUID(),
        legacy,
        LocalDate.of(2026, 6, 1));
    String userWrite = converter.convertToDatabaseColumn(70_000_000L);
    doAnswer(
            call -> {
              // 재암호화가 이 행을 읽고 복호화하는 사이 사용자 수정이 먼저 커밋된다
              jdbcTemplate.update(
                  "UPDATE core.income_history SET gross_salary_encrypted = ? WHERE id = ?",
                  userWrite,
                  incomeId);
              return call.callRealMethod();
            })
        .when(converter)
        .convertToEntityAttribute(eq(legacy));

    int updated = rekeyingService.rekeyIncomeHistory();
    em.clear();

    assertThat(updated).isZero();
    assertThat(em.find(IncomeHistory.class, incomeId).getGrossSalary()).isEqualTo(70_000_000L);
    assertThat(rekeyingService.checkStatus().isFullyMigrated()).isTrue();
  }

  @Test
  @DisplayName("소비 항목도 재암호화 도중의 사용자 수정을 덮어쓰지 않는다")
  void rekeyExpense_doesNotOverwriteConcurrentUserWrite() {
    String legacy = legacyCiphertext(450_000L);
    ExpenseHistory history = new ExpenseHistory(UUID.randomUUID(), "202606");
    em.persistAndFlush(history);
    UUID itemId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO core.expense_item (id, expense_history_id, category, amount_encrypted) "
            + "VALUES (?, ?, ?, ?)",
        itemId,
        history.getId(),
        ExpenseCategoryEnum.FOOD.name(),
        legacy);
    String userWrite = converter.convertToDatabaseColumn(500_000L);
    racingUserWrite(
        legacy,
        "UPDATE core.expense_item SET amount_encrypted = ? WHERE id = ?",
        userWrite,
        itemId);

    assertThat(rekeyingService.rekeyExpenseItem()).isZero();
    em.clear();

    assertThat(em.find(ExpenseItemEntity.class, itemId).getAmount()).isEqualTo(500_000L);
  }

  @Test
  @DisplayName("시나리오도 재암호화 도중의 사용자 수정을 덮어쓰지 않는다")
  void rekeyScenario_doesNotOverwriteConcurrentUserWrite() {
    String legacy = legacyCiphertext(60_000_000L);
    UUID scenarioId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO core.career_scenario (id, user_id, scenario_name, current_gross_salary_encrypted, "
            + "stay_expected_raise_rate, move_target_gross_salary_encrypted, move_one_time_cost, "
            + "expected_unemployment_months, is_eligible_for_unemployment_benefit, stay_compensation_12m_encrypted, "
            + "move_compensation_12m_encrypted, estimated_unemployment_benefit_encrypted, difference_encrypted, "
            + "feasibility_status, calculated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        scenarioId,
        UUID.randomUUID(),
        "시나리오",
        legacy,
        new BigDecimal("5.00"),
        legacy,
        0L,
        0,
        false,
        legacy,
        legacy,
        legacy,
        legacy,
        "OK",
        OffsetDateTime.now());
    String userWrite = converter.convertToDatabaseColumn(61_000_000L);
    racingUserWrite(
        legacy,
        "UPDATE core.career_scenario SET current_gross_salary_encrypted = ? WHERE id = ?",
        userWrite,
        scenarioId);

    assertThat(rekeyingService.rekeyCareerScenario()).isZero();
    em.clear();

    assertThat(em.find(CareerScenario.class, scenarioId).getCurrentGrossSalary())
        .isEqualTo(61_000_000L);
  }

  @Test
  @DisplayName("고정상여 컬럼만 옛 키여도 미전환으로 잡히고 재암호화 뒤 복호화된다 - null 은 건너뛴다")
  void rekey_migratesFixedBonusColumn() {
    String legacy = legacyCiphertext(3_000_000L);
    String activeGross = converter.convertToDatabaseColumn(65_000_000L);
    UUID withBonus = UUID.randomUUID();
    UUID withoutBonus = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO core.income_history (id, user_id, gross_salary_encrypted, "
            + "fixed_bonus_annual_encrypted, effective_date) VALUES (?, ?, ?, ?, ?)",
        withBonus,
        UUID.randomUUID(),
        activeGross,
        legacy,
        LocalDate.of(2026, 6, 1));
    jdbcTemplate.update(
        "INSERT INTO core.income_history (id, user_id, gross_salary_encrypted, effective_date) "
            + "VALUES (?, ?, ?, ?)",
        withoutBonus,
        UUID.randomUUID(),
        activeGross,
        LocalDate.of(2026, 7, 1));
    em.clear();

    assertThat(rekeyingService.checkStatus().isFullyMigrated()).isFalse();
    assertThat(rekeyingService.checkStatus().outdatedIncomeHistory()).isEqualTo(1);

    assertThat(rekeyingService.rekeyAll().incomeHistoryUpdated()).isEqualTo(1);
    em.clear();

    assertThat(rekeyingService.checkStatus().isFullyMigrated()).isTrue();
    assertThat(em.find(IncomeHistory.class, withBonus).getFixedBonusAnnual()).isEqualTo(3_000_000L);
    assertThat(em.find(IncomeHistory.class, withoutBonus).getFixedBonusAnnual()).isNull();
  }

  @Test
  @DisplayName("성과급 금액도 미전환으로 잡히고 재암호화 뒤 복호화된다")
  void rekey_migratesPerformanceBonus() {
    UUID bonusId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO core.performance_bonus (id, user_id, paid_month, amount_encrypted) "
            + "VALUES (?, ?, ?, ?)",
        bonusId,
        UUID.randomUUID(),
        "202606",
        legacyCiphertext(5_000_000L));
    em.clear();

    SensitiveDataRekeyingService.RekeyingStatus before = rekeyingService.checkStatus();
    assertThat(before.outdatedPerformanceBonus()).isEqualTo(1);
    assertThat(before.isFullyMigrated()).isFalse();

    SensitiveDataRekeyingService.RekeyingResult result = rekeyingService.rekeyAll();
    em.clear();

    assertThat(result.performanceBonusUpdated()).isEqualTo(1);
    assertThat(rekeyingService.checkStatus().isFullyMigrated()).isTrue();
    assertThat(em.find(PerformanceBonus.class, bonusId).getAmount()).isEqualTo(5_000_000L);
  }

  @Test
  @DisplayName("성과급도 재암호화 도중의 사용자 수정을 덮어쓰지 않는다")
  void rekeyPerformanceBonus_doesNotOverwriteConcurrentUserWrite() {
    String legacy = legacyCiphertext(5_000_000L);
    UUID bonusId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO core.performance_bonus (id, user_id, paid_month, amount_encrypted) "
            + "VALUES (?, ?, ?, ?)",
        bonusId,
        UUID.randomUUID(),
        "202606",
        legacy);
    racingUserWrite(
        legacy,
        "UPDATE core.performance_bonus SET amount_encrypted = ? WHERE id = ?",
        converter.convertToDatabaseColumn(6_000_000L),
        bonusId);

    assertThat(rekeyingService.rekeyPerformanceBonus()).isZero();
    em.clear();

    assertThat(em.find(PerformanceBonus.class, bonusId).getAmount()).isEqualTo(6_000_000L);
  }

  /** 재암호화가 {@code legacy} 를 처음 복호화하는 순간, 그 전에 사용자 수정이 커밋된 것처럼 행을 바꾼다. */
  private void racingUserWrite(String legacy, String sql, String userWrite, UUID id) {
    boolean[] fired = {false};
    doAnswer(
            call -> {
              if (!fired[0]) {
                fired[0] = true;
                jdbcTemplate.update(sql, userWrite, id);
              }
              return call.callRealMethod();
            })
        .when(converter)
        .convertToEntityAttribute(eq(legacy));
  }

  private String legacyCiphertext(long amount) {
    String encrypted = converter.convertToDatabaseColumn(amount);
    return encrypted.substring(encrypted.indexOf(':') + 1);
  }
}
