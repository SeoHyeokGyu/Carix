package com.carix.income.service;

import com.carix.common.EncryptedConverter;
import com.carix.common.KeyRing;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 키 로테이션 시 기존 암호화 컬럼들을 활성 키(active key)로 점진적 재암호화(rekeying)하는 서비스.
 *
 * <p>대상 테이블 및 컬럼 (SECURITY §3.2, 기술 명세 §1.1):
 *
 * <ul>
 *   <li>{@code core.income_history} (gross_salary_encrypted, net_salary_encrypted)
 *   <li>{@code core.expense_item} (amount_encrypted)
 *   <li>{@code core.career_scenario} (6개 암호화 컬럼)
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveDataRekeyingService {

  private final EncryptedConverter converter;
  private final KeyRing keyRing;
  private final JdbcTemplate jdbcTemplate;

  /** 재암호화 결과 보고서 */
  public record RekeyingResult(
      int incomeHistoryUpdated,
      int expenseItemUpdated,
      int careerScenarioUpdated,
      String activeKeyId) {
    public int totalUpdated() {
      return incomeHistoryUpdated + expenseItemUpdated + careerScenarioUpdated;
    }
  }

  /** 미전환 잔여 암호문 현황 */
  public record RekeyingStatus(
      long outdatedIncomeHistory,
      long outdatedExpenseItem,
      long outdatedCareerScenario,
      String activeKeyId) {
    public boolean isFullyMigrated() {
      return outdatedIncomeHistory == 0 && outdatedExpenseItem == 0 && outdatedCareerScenario == 0;
    }

    public long totalOutdated() {
      return outdatedIncomeHistory + outdatedExpenseItem + outdatedCareerScenario;
    }
  }

  /** 미전환 잔여 건수를 확인한다. */
  @Transactional(readOnly = true)
  public RekeyingStatus checkStatus() {
    String activePrefix = keyRing.getActiveKeyId() + ":%";

    Long income =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM core.income_history "
                + "WHERE gross_salary_encrypted NOT LIKE ? "
                + "OR (net_salary_encrypted IS NOT NULL AND net_salary_encrypted NOT LIKE ?)",
            Long.class,
            activePrefix,
            activePrefix);

    Long expense =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM core.expense_item WHERE amount_encrypted NOT LIKE ?",
            Long.class,
            activePrefix);

    Long scenario =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM core.career_scenario "
                + "WHERE current_gross_salary_encrypted NOT LIKE ? "
                + "OR move_target_gross_salary_encrypted NOT LIKE ? "
                + "OR stay_compensation_12m_encrypted NOT LIKE ? "
                + "OR move_compensation_12m_encrypted NOT LIKE ? "
                + "OR estimated_unemployment_benefit_encrypted NOT LIKE ? "
                + "OR difference_encrypted NOT LIKE ?",
            Long.class,
            activePrefix,
            activePrefix,
            activePrefix,
            activePrefix,
            activePrefix,
            activePrefix);

    return new RekeyingStatus(
        income != null ? income : 0L,
        expense != null ? expense : 0L,
        scenario != null ? scenario : 0L,
        keyRing.getActiveKeyId());
  }

  /** 모든 대상 테이블의 구 암호문을 활성 키로 재암호화한다. */
  @Transactional
  public RekeyingResult rekeyAll() {
    int income = rekeyIncomeHistory();
    int expense = rekeyExpenseItem();
    int scenario = rekeyCareerScenario();

    log.info(
        "키 로테이션 재암호화 완료: activeKeyId={}, incomeHistory={}, expenseItem={}, careerScenario={}",
        keyRing.getActiveKeyId(),
        income,
        expense,
        scenario);

    return new RekeyingResult(income, expense, scenario, keyRing.getActiveKeyId());
  }

  /** income_history 테이블 재암호화 */
  @Transactional
  public int rekeyIncomeHistory() {
    String activePrefix = keyRing.getActiveKeyId() + ":%";

    return jdbcTemplate.query(
        "SELECT id, gross_salary_encrypted, net_salary_encrypted FROM core.income_history "
            + "WHERE gross_salary_encrypted NOT LIKE ? "
            + "OR (net_salary_encrypted IS NOT NULL AND net_salary_encrypted NOT LIKE ?)",
        rs -> {
          int count = 0;
          while (rs.next()) {
            UUID id = rs.getObject("id", UUID.class);
            String rawGross = rs.getString("gross_salary_encrypted");
            String rawNet = rs.getString("net_salary_encrypted");

            Long gross = converter.convertToEntityAttribute(rawGross);
            String newGross = converter.convertToDatabaseColumn(gross);

            String newNet = null;
            if (rawNet != null) {
              Long net = converter.convertToEntityAttribute(rawNet);
              newNet = converter.convertToDatabaseColumn(net);
            }

            jdbcTemplate.update(
                "UPDATE core.income_history SET gross_salary_encrypted = ?, net_salary_encrypted = ? WHERE id = ?",
                newGross,
                newNet,
                id);
            count++;
          }
          return count;
        },
        activePrefix,
        activePrefix);
  }

  /** expense_item 테이블 재암호화 */
  @Transactional
  public int rekeyExpenseItem() {
    String activePrefix = keyRing.getActiveKeyId() + ":%";

    return jdbcTemplate.query(
        "SELECT id, amount_encrypted FROM core.expense_item WHERE amount_encrypted NOT LIKE ?",
        rs -> {
          int count = 0;
          while (rs.next()) {
            UUID id = rs.getObject("id", UUID.class);
            String rawAmount = rs.getString("amount_encrypted");

            Long amount = converter.convertToEntityAttribute(rawAmount);
            String newAmount = converter.convertToDatabaseColumn(amount);

            jdbcTemplate.update(
                "UPDATE core.expense_item SET amount_encrypted = ? WHERE id = ?", newAmount, id);
            count++;
          }
          return count;
        },
        activePrefix);
  }

  /** career_scenario 테이블 재암호화 */
  @Transactional
  public int rekeyCareerScenario() {
    String activePrefix = keyRing.getActiveKeyId() + ":%";

    return jdbcTemplate.query(
        "SELECT id, current_gross_salary_encrypted, move_target_gross_salary_encrypted, "
            + "stay_compensation_12m_encrypted, move_compensation_12m_encrypted, "
            + "estimated_unemployment_benefit_encrypted, difference_encrypted "
            + "FROM core.career_scenario "
            + "WHERE current_gross_salary_encrypted NOT LIKE ? "
            + "OR move_target_gross_salary_encrypted NOT LIKE ? "
            + "OR stay_compensation_12m_encrypted NOT LIKE ? "
            + "OR move_compensation_12m_encrypted NOT LIKE ? "
            + "OR estimated_unemployment_benefit_encrypted NOT LIKE ? "
            + "OR difference_encrypted NOT LIKE ?",
        rs -> {
          int count = 0;
          while (rs.next()) {
            UUID id = rs.getObject("id", UUID.class);

            String newCurrentGross =
                converter.convertToDatabaseColumn(
                    converter.convertToEntityAttribute(
                        rs.getString("current_gross_salary_encrypted")));
            String newMoveTargetGross =
                converter.convertToDatabaseColumn(
                    converter.convertToEntityAttribute(
                        rs.getString("move_target_gross_salary_encrypted")));
            String newStayComp =
                converter.convertToDatabaseColumn(
                    converter.convertToEntityAttribute(
                        rs.getString("stay_compensation_12m_encrypted")));
            String newMoveComp =
                converter.convertToDatabaseColumn(
                    converter.convertToEntityAttribute(
                        rs.getString("move_compensation_12m_encrypted")));
            String newUnemployment =
                converter.convertToDatabaseColumn(
                    converter.convertToEntityAttribute(
                        rs.getString("estimated_unemployment_benefit_encrypted")));
            String newDiff =
                converter.convertToDatabaseColumn(
                    converter.convertToEntityAttribute(rs.getString("difference_encrypted")));

            jdbcTemplate.update(
                "UPDATE core.career_scenario SET "
                    + "current_gross_salary_encrypted = ?, "
                    + "move_target_gross_salary_encrypted = ?, "
                    + "stay_compensation_12m_encrypted = ?, "
                    + "move_compensation_12m_encrypted = ?, "
                    + "estimated_unemployment_benefit_encrypted = ?, "
                    + "difference_encrypted = ? "
                    + "WHERE id = ?",
                newCurrentGross,
                newMoveTargetGross,
                newStayComp,
                newMoveComp,
                newUnemployment,
                newDiff,
                id);
            count++;
          }
          return count;
        },
        activePrefix,
        activePrefix,
        activePrefix,
        activePrefix,
        activePrefix,
        activePrefix);
  }
}
