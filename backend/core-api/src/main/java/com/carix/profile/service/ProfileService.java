package com.carix.profile.service;

import com.carix.profile.domain.CareerProfile;
import com.carix.profile.dto.ProfileRequest;
import com.carix.profile.dto.ProfileResponse;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.snapshot.SnapshotRecalculationService;
import com.carix.snapshot.YearMonths;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

  private final CareerProfileRepository careerProfileRepository;
  private final SnapshotRecalculationService snapshotRecalculationService;

  public ProfileService(
      CareerProfileRepository careerProfileRepository,
      SnapshotRecalculationService snapshotRecalculationService) {
    this.careerProfileRepository = careerProfileRepository;
    this.snapshotRecalculationService = snapshotRecalculationService;
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
