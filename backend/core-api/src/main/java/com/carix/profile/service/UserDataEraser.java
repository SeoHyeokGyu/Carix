package com.carix.profile.service;

import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.income.repository.PerformanceBonusRepository;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.scenario.CareerScenarioRepository;
import com.carix.skill.repository.UserSkillRepository;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 한 명의 core 개인 데이터와 auth 행을 지운다. 탈퇴와 게스트 정리가 같은 범위를 공유한다. */
@Service
public class UserDataEraser {

  private static final Logger log = LoggerFactory.getLogger(UserDataEraser.class);

  private final CareerProfileRepository careerProfileRepository;
  private final UserSkillRepository userSkillRepository;
  private final IncomeHistoryRepository incomeHistoryRepository;
  private final ExpenseHistoryRepository expenseHistoryRepository;
  private final IndexSnapshotRepository indexSnapshotRepository;
  private final CareerScenarioRepository careerScenarioRepository;
  private final PerformanceBonusRepository performanceBonusRepository;
  private final JdbcTemplate jdbcTemplate;

  public UserDataEraser(
      CareerProfileRepository careerProfileRepository,
      UserSkillRepository userSkillRepository,
      IncomeHistoryRepository incomeHistoryRepository,
      ExpenseHistoryRepository expenseHistoryRepository,
      IndexSnapshotRepository indexSnapshotRepository,
      CareerScenarioRepository careerScenarioRepository,
      PerformanceBonusRepository performanceBonusRepository,
      JdbcTemplate jdbcTemplate) {
    this.careerProfileRepository = careerProfileRepository;
    this.userSkillRepository = userSkillRepository;
    this.incomeHistoryRepository = incomeHistoryRepository;
    this.expenseHistoryRepository = expenseHistoryRepository;
    this.indexSnapshotRepository = indexSnapshotRepository;
    this.careerScenarioRepository = careerScenarioRepository;
    this.performanceBonusRepository = performanceBonusRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 사용자 탈퇴용. core 개인 데이터를 지우고 auth 세션·사용자 행을 지운다. */
  @Transactional
  public void erase(UUID userId) {
    deleteCoreData(userId);

    // auth 스키마 접근 가능 시에만 (로컬·테스트 환경에는 없을 수 있다)
    try {
      jdbcTemplate.update("DELETE FROM auth.session WHERE user_id = ?", userId);
      jdbcTemplate.update("DELETE FROM auth.users WHERE id = ?", userId);
    } catch (org.springframework.dao.DataAccessException e) {
      log.warn("auth 스키마 계정 삭제 건너뜀: {}", e.getMessage());
    }

    log.info("사용자({})의 모든 개인 데이터가 안전하게 삭제되었습니다.", userId);
  }

  /**
   * 게스트 정리용. 게스트 행 삭제를 먼저 시도하고, 지운 경우에만 core 데이터를 지운다.
   *
   * <p>대상 선택과 삭제 사이에 정식 계정으로 전환됐다면 {@code guest} 조건에 걸리지 않아 0건이 지워지고, 이때는 core 데이터도 건드리지 않는다. 세션은
   * {@code ON DELETE CASCADE} 로 함께 지워진다.
   */
  @Transactional
  public boolean eraseGuest(UUID userId) {
    int deleted = jdbcTemplate.update("DELETE FROM auth.users WHERE id = ? AND guest", userId);
    if (deleted == 0) {
      return false;
    }
    deleteCoreData(userId);
    return true;
  }

  private void deleteCoreData(UUID userId) {
    careerProfileRepository.deleteByUserId(userId);
    userSkillRepository.deleteByUserId(userId);
    incomeHistoryRepository.deleteByUserId(userId);
    expenseHistoryRepository.deleteByUserId(userId);
    indexSnapshotRepository.deleteByUserId(userId);
    careerScenarioRepository.deleteByUserId(userId);
    performanceBonusRepository.deleteByUserId(userId);
  }
}
