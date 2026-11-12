package com.carix.profile.service;

import com.carix.profile.domain.CareerProfile;
import com.carix.profile.dto.ProfileRequest;
import com.carix.profile.dto.ProfileResponse;
import com.carix.profile.exception.InvalidHireDateException;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.snapshot.SnapshotRecalculationRequested;
import com.carix.snapshot.YearMonths;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

  private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

  private final CareerProfileRepository careerProfileRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final UserDataEraser eraser;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  public ProfileService(
      CareerProfileRepository careerProfileRepository,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this(careerProfileRepository, eventPublisher, null, null, clock);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public ProfileService(
      CareerProfileRepository careerProfileRepository,
      ApplicationEventPublisher eventPublisher,
      UserDataEraser eraser,
      JdbcTemplate jdbcTemplate,
      Clock clock) {
    this.careerProfileRepository = careerProfileRepository;
    this.eventPublisher = eventPublisher;
    this.eraser = eraser;
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
  }

  /**
   * 사용자 및 개인 데이터 완전 삭제 (US-C2).
   *
   * <p>프로필, 보유 스킬, 소득/소비 이력, 성과급 기록, 지수 스냅샷, 커리어 시나리오 및 세션/사용자 행을 영구 삭제한다. 공용 시장
   * 집계(MarketAggregate) 데이터는 영향을 받지 않는다.
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
          // 게스트 행은 이메일이 없다. 불일치와 같은 응답으로 막는다.
          if (registeredEmail == null || !registeredEmail.trim().equalsIgnoreCase(email.trim())) {
            throw new IllegalArgumentException("입력하신 이메일이 회원 정보와 일치하지 않습니다.");
          }
        }
      } catch (org.springframework.dao.DataAccessException e) {
        log.warn("auth.users 이메일 검증 건너뜀 (auth 스키마 미존재 또는 접근 불가): {}", e.getMessage());
      }
    }

    // 2. core 개인 데이터와 auth 세션·사용자 행 삭제
    eraser.erase(userId);
  }

  @Transactional(readOnly = true)
  public ProfileResponse get(UUID userId) {
    return careerProfileRepository
        .findByUserId(userId)
        .map(ProfileService::toResponse)
        .orElseGet(() -> new ProfileResponse(null, null, null, null, null, null, null));
  }

  @Transactional
  public ProfileResponse patch(UUID userId, ProfileRequest request) {
    if (request.hireDate() != null && request.hireDate().isAfter(LocalDate.now(clock))) {
      throw new InvalidHireDateException("입사일은 오늘 이후일 수 없습니다.");
    }
    CareerProfile profile =
        careerProfileRepository
            .findByUserId(userId)
            .orElseGet(() -> careerProfileRepository.save(new CareerProfile(userId)));
    profile.patch(
        request.jobFamily(),
        request.yearsOfExperience(),
        request.region(),
        request.employmentType(),
        request.companySize(),
        request.hireDate(),
        request.retirementPensionType());
    // 동료집단 조건이 바뀌면 이후 MVI 가 달라진다. 과거월은 그때의 프로필로 계산된 값이라 다시 쓰지 않는다.
    eventPublisher.publishEvent(new SnapshotRecalculationRequested(userId, YearMonths.current()));
    return toResponse(profile);
  }

  private static ProfileResponse toResponse(CareerProfile profile) {
    return new ProfileResponse(
        profile.getJobFamily(),
        profile.getYearsOfExperience(),
        profile.getRegion(),
        profile.getEmploymentType(),
        profile.getCompanySize(),
        profile.getHireDate(),
        profile.getRetirementPensionType());
  }
}
