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
 * 키 로테이션 시 기존 데이터베이스에 저장된 암호화 컬럼들을 최신 활성 키(Active Key)로 점진적 재암호화(Rekeying)하는 서비스.
 *
 * <p><b>배경 및 구현 원리 (SECURITY §3.2, 기술 명세 §1.1):</b>
 *
 * <ol>
 *   <li><b>JPA 더티 체킹(Dirty Checking) 우회 필요성:</b> JPA 엔티티를 로딩하면 복호화된 {@code Long} 금액이 메모리에 올라온다. 이
 *       값이 동일하면 Hibernate는 변경 사항이 없다고 판단하여 {@code UPDATE} 쿼리를 발행하지 않는다. 따라서 데이터베이스의 물리적 컬럼 ({@code
 *       ..._encrypted})을 직접 다루기 위해 {@link JdbcTemplate}을 사용하여 직접 조회·갱신한다.
 *   <li><b>멱등성 및 증분 마이그레이션 (Incremental Migration):</b> 이미 최신 활성 키 접두사({@code activeKeyId + ":%"})로
 *       암호화된 행은 쿼리 조건({@code NOT LIKE ?})에서 자동으로 제외되므로, 재실행하더라도 안전하고 중복 연산이 발생하지 않는다.
 *   <li><b>민감 정보 로깅 방지:</b> 로테이션 과정에서 어떠한 평문 금액이나 암호화 키 값도 로그에 출력하지 않으며, 오직 대상 테이블별 갱신 건수와 {@code
 *       activeKeyId} 메타데이터만 로깅한다 (완료 기준 10).
 * </ol>
 *
 * <p><b>대상 테이블 및 컬럼:</b>
 *
 * <ul>
 *   <li>{@code core.income_history} (세전 연봉: {@code gross_salary_encrypted}, 월 실수령액: {@code
 *       net_salary_encrypted})
 *   <li>{@code core.expense_item} (월 소비액: {@code amount_encrypted})
 *   <li>{@code core.career_scenario} (시뮬레이션 관련 금액 6개 컬럼)
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveDataRekeyingService {

  private final EncryptedConverter converter;
  private final KeyRing keyRing;
  private final JdbcTemplate jdbcTemplate;

  /**
   * 재암호화 배치 실행 결과 보고서.
   *
   * @param incomeHistoryUpdated 갱신된 소득 이력 행 수
   * @param expenseItemUpdated 갱신된 소비 품목 행 수
   * @param careerScenarioUpdated 갱신된 시뮬레이션 시나리오 행 수
   * @param activeKeyId 마이그레이션 목표로 사용된 활성 키 ID
   */
  public record RekeyingResult(
      int incomeHistoryUpdated,
      int expenseItemUpdated,
      int careerScenarioUpdated,
      String activeKeyId) {

    /** 전체 갱신된 행 수의 합계 */
    public int totalUpdated() {
      return incomeHistoryUpdated + expenseItemUpdated + careerScenarioUpdated;
    }
  }

  /**
   * 미전환 잔여 암호문 현황 보고서.
   *
   * @param outdatedIncomeHistory 활성 키로 아직 전환되지 않은 소득 이력 행 수
   * @param outdatedExpenseItem 활성 키로 아직 전환되지 않은 소비 품목 행 수
   * @param outdatedCareerScenario 활성 키로 아직 전환되지 않은 시뮬레이션 시나리오 행 수
   * @param activeKeyId 현재 활성 키 ID
   */
  public record RekeyingStatus(
      long outdatedIncomeHistory,
      long outdatedExpenseItem,
      long outdatedCareerScenario,
      String activeKeyId) {

    /**
     * 모든 대상 테이블의 암호문이 현재 활성 키로 100% 전환 완료되었는지 여부. 이 값이 {@code true}인 경우에만 이전 구 키를 안전하게 폐기할 수 있다.
     */
    public boolean isFullyMigrated() {
      return outdatedIncomeHistory == 0 && outdatedExpenseItem == 0 && outdatedCareerScenario == 0;
    }

    /** 전체 미전환 행 수의 합계 */
    public long totalOutdated() {
      return outdatedIncomeHistory + outdatedExpenseItem + outdatedCareerScenario;
    }
  }

  /**
   * 현재 활성 키로 아직 재암호화되지 않은 각 테이블별 잔여 행 수를 조회한다.
   *
   * <p>운영자가 구 키 폐기 전 마이그레이션 완료 여부를 검증할 때 사용한다.
   *
   * @return 테이블별 미전환 건수가 담긴 상태 객체
   */
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

  /**
   * 모든 대상 테이블(소득, 소비, 시나리오)의 구버전 암호문을 현재 활성 키로 일괄 재암호화한다.
   *
   * @return 각 테이블별 갱신된 레코드 수 보고서
   */
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

  /**
   * {@code core.income_history} 테이블의 세전 연봉 및 실수령액을 최신 활성 키로 재암호화한다.
   *
   * @return 재암호화된 소득 이력 건수
   */
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

            // 1. 기존 암호문 복호화 (구 키 또는 레거시 키 자동 판별)
            Long gross = converter.convertToEntityAttribute(rawGross);
            // 2. 현재 활성 키로 신규 암호화 (새로운 랜덤 IV + activeKeyId 접두사 적용)
            String newGross = converter.convertToDatabaseColumn(gross);

            String newNet = null;
            if (rawNet != null) {
              Long net = converter.convertToEntityAttribute(rawNet);
              newNet = converter.convertToDatabaseColumn(net);
            }

            // 3. 물리적 컬럼 직접 업데이트
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

  /**
   * {@code core.expense_item} 테이블의 월 소비액을 최신 활성 키로 재암호화한다.
   *
   * @return 재암호화된 소비 항목 건수
   */
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

  /**
   * {@code core.career_scenario} 테이블의 6개 시뮬레이션 금액 컬럼을 최신 활성 키로 재암호화한다.
   *
   * @return 재암호화된 시나리오 레코드 건수
   */
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

            // 6개 암호화 금액 컬럼 각각에 대해 복호화 후 신규 활성 키로 재암호화
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
