package com.carix.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.income.repository.PerformanceBonusRepository;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.dto.ProfileRequest;
import com.carix.profile.exception.InvalidHireDateException;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.scenario.CareerScenarioRepository;
import com.carix.skill.repository.UserSkillRepository;
import com.carix.snapshot.SnapshotRecalculationService;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ProfileServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  // 서울 2026-10-22 00:30 = UTC 2026-10-21 15:30 — JVM 기본 TZ 가 UTC 여도 오늘은 10-22 여야 한다
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-21T15:30:00Z"), ZoneId.of("Asia/Seoul"));

  private CareerProfileRepository careerProfileRepository;
  private SnapshotRecalculationService snapshotRecalculationService;
  private UserSkillRepository userSkillRepository;
  private IncomeHistoryRepository incomeHistoryRepository;
  private ExpenseHistoryRepository expenseHistoryRepository;
  private IndexSnapshotRepository indexSnapshotRepository;
  private CareerScenarioRepository careerScenarioRepository;
  private PerformanceBonusRepository performanceBonusRepository;
  private JdbcTemplate jdbcTemplate;

  private ProfileService profileService;

  @BeforeEach
  void setUp() {
    careerProfileRepository = mock(CareerProfileRepository.class);
    snapshotRecalculationService = mock(SnapshotRecalculationService.class);
    userSkillRepository = mock(UserSkillRepository.class);
    incomeHistoryRepository = mock(IncomeHistoryRepository.class);
    expenseHistoryRepository = mock(ExpenseHistoryRepository.class);
    indexSnapshotRepository = mock(IndexSnapshotRepository.class);
    careerScenarioRepository = mock(CareerScenarioRepository.class);
    performanceBonusRepository = mock(PerformanceBonusRepository.class);
    jdbcTemplate = mock(JdbcTemplate.class);

    profileService =
        new ProfileService(
            careerProfileRepository,
            snapshotRecalculationService,
            userSkillRepository,
            incomeHistoryRepository,
            expenseHistoryRepository,
            indexSnapshotRepository,
            careerScenarioRepository,
            performanceBonusRepository,
            jdbcTemplate,
            CLOCK);
  }

  @Test
  @DisplayName("deleteUser - 등록 이메일과 일치하면 모든 개인 데이터 테이블을 삭제한다")
  void deleteUser_정상() {
    String email = "test@example.com";
    given(
            jdbcTemplate.query(
                anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(USER_ID)))
        .willReturn(List.of(email));

    profileService.deleteUser(USER_ID, email);

    verify(careerProfileRepository).deleteByUserId(USER_ID);
    verify(userSkillRepository).deleteByUserId(USER_ID);
    verify(incomeHistoryRepository).deleteByUserId(USER_ID);
    verify(expenseHistoryRepository).deleteByUserId(USER_ID);
    verify(indexSnapshotRepository).deleteByUserId(USER_ID);
    verify(careerScenarioRepository).deleteByUserId(USER_ID);
    verify(performanceBonusRepository).deleteByUserId(USER_ID);
  }

  @Test
  @DisplayName("deleteUser - 이메일이 불일치하면 예외가 발생하고 데이터는 삭제되지 않는다")
  void deleteUser_이메일_불일치() {
    given(
            jdbcTemplate.query(
                anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(USER_ID)))
        .willReturn(List.of("registered@example.com"));

    assertThatThrownBy(() -> profileService.deleteUser(USER_ID, "wrong@example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("일치하지 않습니다");
  }

  @Test
  @DisplayName("patch - 서울 기준 오늘 입사일은 허용하고 내일은 거부한다")
  void 입사일_미래_판정은_서울_시계_기준() {
    CareerProfile profile = new CareerProfile(USER_ID);
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));

    var res =
        profileService.patch(
            USER_ID, new ProfileRequest(null, null, null, null, null, LocalDate.of(2026, 10, 22)));
    assertThat(res.hireDate()).isEqualTo(LocalDate.of(2026, 10, 22));

    assertThatThrownBy(
            () ->
                profileService.patch(
                    USER_ID,
                    new ProfileRequest(null, null, null, null, null, LocalDate.of(2026, 10, 23))))
        .isInstanceOf(InvalidHireDateException.class);
  }

  @Test
  @DisplayName("patch - hireDate 없이 보내면 기존 입사일을 유지한다")
  void hireDate_없이_패치하면_기존_입사일_유지() {
    CareerProfile profile = new CareerProfile(USER_ID);
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
    profileService.patch(
        USER_ID, new ProfileRequest(null, null, null, null, null, LocalDate.of(2022, 3, 2)));

    var res =
        profileService.patch(USER_ID, new ProfileRequest("DEVELOPER", 5, null, null, null, null));

    assertThat(res.hireDate()).isEqualTo(LocalDate.of(2022, 3, 2));
  }
}
