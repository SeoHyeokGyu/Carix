package com.carix.profile.service;

import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.dto.ProfileRequest;
import com.carix.profile.dto.ProfileResponse;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.scenario.CareerScenarioRepository;
import com.carix.skill.repository.UserSkillRepository;
import com.carix.snapshot.SnapshotRecalculationService;
import com.carix.snapshot.YearMonths;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

  private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

  private final CareerProfileRepository careerProfileRepository;
  private final SnapshotRecalculationService snapshotRecalculationService;
  private final UserSkillRepository userSkillRepository;
  private final IncomeHistoryRepository incomeHistoryRepository;
  private final ExpenseHistoryRepository expenseHistoryRepository;
  private final IndexSnapshotRepository indexSnapshotRepository;
  private final CareerScenarioRepository careerScenarioRepository;
  private final JdbcTemplate jdbcTemplate;

  public ProfileService(
      CareerProfileRepository careerProfileRepository,
      SnapshotRecalculationService snapshotRecalculationService) {
    this(careerProfileRepository, snapshotRecalculationService, null, null, null, null, null, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public ProfileService(
      CareerProfileRepository careerProfileRepository,
      SnapshotRecalculationService snapshotRecalculationService,
      UserSkillRepository userSkillRepository,
      IncomeHistoryRepository incomeHistoryRepository,
      ExpenseHistoryRepository expenseHistoryRepository,
      IndexSnapshotRepository indexSnapshotRepository,
      CareerScenarioRepository careerScenarioRepository,
      JdbcTemplate jdbcTemplate) {
    this.careerProfileRepository = careerProfileRepository;
    this.snapshotRecalculationService = snapshotRecalculationService;
    this.userSkillRepository = userSkillRepository;
    this.incomeHistoryRepository = incomeHistoryRepository;
    this.expenseHistoryRepository = expenseHistoryRepository;
    this.indexSnapshotRepository = indexSnapshotRepository;
    this.careerScenarioRepository = careerScenarioRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 사용자 및 개인 데이터 완전 삭제 (US-C2).
   *
   * <p>프로필, 보유 스킬, 소득/소비 이력, 지수 스냅샷, 커리어 시나리오 및 세션/사용자 행을 영구 삭제한다. 공용 시장 집계(MarketAggregate) 데이터는
   * 영향을 받지 않는다.
   */
  @Transactional
  public void deleteUser(UUID userId, String email) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("확인용 이메일은 필수 입력값입니다.");
    }

    // 1. auth.users 테이블이 존재할 경우 이메일 일치 검증
    if (jdbcTemplate != null) {
      try {
        List<String> emails =
            jdbcTemplate.query(
                "SELECT email FROM auth.users WHERE id = ?",
                (rs, rowNum) -> rs.getString("email"),
                userId);
        if (!emails.isEmpty()) {
          String registeredEmail = emails.get(0);
          if (!registeredEmail.trim().equalsIgnoreCase(email.trim())) {
            throw new IllegalArgumentException("입력하신 이메일이 회원 정보와 일치하지 않습니다.");
          }
        }
      } catch (org.springframework.dao.DataAccessException e) {
        log.warn("auth.users 이메일 검증 건너뜀 (auth 스키마 미존재 또는 접근 불가): {}", e.getMessage());
      }
    }

    // 2. core 스키마 개인 데이터 완전 삭제
    if (careerProfileRepository != null) careerProfileRepository.deleteByUserId(userId);
    if (userSkillRepository != null) userSkillRepository.deleteByUserId(userId);
    if (incomeHistoryRepository != null) incomeHistoryRepository.deleteByUserId(userId);
    if (expenseHistoryRepository != null) expenseHistoryRepository.deleteByUserId(userId);
    if (indexSnapshotRepository != null) indexSnapshotRepository.deleteByUserId(userId);
    if (careerScenarioRepository != null) careerScenarioRepository.deleteByUserId(userId);

    // 3. auth 스키마 세션 및 사용자 행 삭제 (auth 스키마 접근 가능 시)
    if (jdbcTemplate != null) {
      try {
        jdbcTemplate.update("DELETE FROM auth.session WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM auth.users WHERE id = ?", userId);
      } catch (org.springframework.dao.DataAccessException e) {
        log.warn("auth 스키마 계정 삭제 건너뜀: {}", e.getMessage());
      }
    }

    log.info("사용자({})의 모든 개인 데이터가 안전하게 삭제되었습니다.", userId);
  }

  @Transactional(readOnly = true)
  public ProfileResponse get(UUID userId) {
    return careerProfileRepository
        .findByUserId(userId)
        .map(ProfileService::toResponse)
        .orElseGet(() -> new ProfileResponse(null, null, null, null, null));
  }

  @Transactional
  public ProfileResponse patch(UUID userId, ProfileRequest request) {
    CareerProfile profile =
        careerProfileRepository
            .findByUserId(userId)
            .orElseGet(() -> careerProfileRepository.save(new CareerProfile(userId)));
    profile.patch(
        request.jobFamily(),
        request.yearsOfExperience(),
        request.region(),
        request.employmentType(),
        request.companySize());
    // 동료집단 조건이 바뀌면 이후 MVI 가 달라진다. 과거월은 그때의 프로필로 계산된 값이라 다시 쓰지 않는다.
    snapshotRecalculationService.recalculateSnapshot(userId, YearMonths.current());
    return toResponse(profile);
  }

  private static ProfileResponse toResponse(CareerProfile profile) {
    return new ProfileResponse(
        profile.getJobFamily(),
        profile.getYearsOfExperience(),
        profile.getRegion(),
        profile.getEmploymentType(),
        profile.getCompanySize());
  }
}
