package com.carix.profile.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.income.repository.PerformanceBonusRepository;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.scenario.CareerScenarioRepository;
import com.carix.skill.repository.UserSkillRepository;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** 삭제 범위 회귀 방지: core 저장소가 하나라도 빠지면 탈퇴·게스트 정리 뒤 개인 데이터가 남는다. */
class UserDataEraserTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private CareerProfileRepository careerProfileRepository;
  private UserSkillRepository userSkillRepository;
  private IncomeHistoryRepository incomeHistoryRepository;
  private ExpenseHistoryRepository expenseHistoryRepository;
  private IndexSnapshotRepository indexSnapshotRepository;
  private CareerScenarioRepository careerScenarioRepository;
  private PerformanceBonusRepository performanceBonusRepository;
  private JdbcTemplate jdbcTemplate;
  private UserDataEraser eraser;

  @BeforeEach
  void setUp() {
    careerProfileRepository = mock(CareerProfileRepository.class);
    userSkillRepository = mock(UserSkillRepository.class);
    incomeHistoryRepository = mock(IncomeHistoryRepository.class);
    expenseHistoryRepository = mock(ExpenseHistoryRepository.class);
    indexSnapshotRepository = mock(IndexSnapshotRepository.class);
    careerScenarioRepository = mock(CareerScenarioRepository.class);
    performanceBonusRepository = mock(PerformanceBonusRepository.class);
    jdbcTemplate = mock(JdbcTemplate.class);
    eraser =
        new UserDataEraser(
            careerProfileRepository,
            userSkillRepository,
            incomeHistoryRepository,
            expenseHistoryRepository,
            indexSnapshotRepository,
            careerScenarioRepository,
            performanceBonusRepository,
            jdbcTemplate);
  }

  @Test
  void 게스트_정리는_core_일곱_저장소의_해당_사용자_행을_모두_지운다() {
    given(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.<Object>any()))
        .willReturn(1);

    eraser.eraseGuest(USER_ID);

    verifyAllCoreDeleted();
  }

  @Test
  void 탈퇴는_core_일곱_저장소의_해당_사용자_행을_모두_지운다() {
    eraser.erase(USER_ID);

    verifyAllCoreDeleted();
  }

  private void verifyAllCoreDeleted() {
    verify(careerProfileRepository).deleteByUserId(USER_ID);
    verify(userSkillRepository).deleteByUserId(USER_ID);
    verify(incomeHistoryRepository).deleteByUserId(USER_ID);
    verify(expenseHistoryRepository).deleteByUserId(USER_ID);
    verify(indexSnapshotRepository).deleteByUserId(USER_ID);
    verify(careerScenarioRepository).deleteByUserId(USER_ID);
    verify(performanceBonusRepository).deleteByUserId(USER_ID);
  }
}
