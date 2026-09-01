package com.carix.profile.service;

import com.carix.profile.domain.CareerProfile;
import com.carix.profile.dto.ProfileRequest;
import com.carix.profile.dto.ProfileResponse;
import com.carix.profile.repository.CareerProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

  private final CareerProfileRepository careerProfileRepository;

  public ProfileService(CareerProfileRepository careerProfileRepository) {
    this.careerProfileRepository = careerProfileRepository;
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
