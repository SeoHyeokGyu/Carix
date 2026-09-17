package com.carix.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.domain.ExpenseHistory;
import com.carix.expense.domain.ExpenseItemEntity;
import com.carix.expense.domain.WeightStatusEnum;
import com.carix.income.domain.IncomeHistory;
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

/**
 * SensitiveDataRekeyingService 통합 테스트.
 *
 * <p>검증 항목:
 * <ul>
 *   <li>1. 접두사가 없는 레거시 암호문이 저장된 테이블(소득, 소비, 시나리오 3종)을 감지하는 {@code checkStatus()} 검증</li>
 *   <li>2. {@code rekeyAll()} 실행 후 모든 암호문이 최신 활성 키 접두사({@code v1:})로 갱신되는지 검증</li>
 *   <li>3. 재암호화 완료 후 엔티티를 조회했을 때 평문 금액이 정상 복호화되는지 정합성 확인</li>
 *   <li>4. 재암호화 완료 후 {@code isFullyMigrated() == true} 및 {@code totalOutdated() == 0} 검증</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SensitiveDataRekeyingService.class, KeyRing.class, EncryptedConverter.class})
class SensitiveDataRekeyingServiceTest {

  @Autowired private SensitiveDataRekeyingService rekeyingService;
  @Autowired private EncryptedConverter converter;
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
        "INSERT INTO core.expense_item (id, expense_history_id, category, amount_encrypted, weight_status) "
            + "VALUES (?, ?, ?, ?, ?)",
        expenseItemId,
        expenseHistory.getId(),
        ExpenseCategoryEnum.FOOD.name(),
        rawLegacyCiphertext,
        WeightStatusEnum.DEFAULT_CPI.name());

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
}
