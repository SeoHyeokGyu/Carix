package com.carix.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.profile.domain.CareerProfile;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CareerProfileRepositoryTest {

  @Autowired private CareerProfileRepository careerProfileRepository;

  @Test
  @DisplayName("findByUserId - 저장한 프로필을 사용자 ID 로 되찾는다")
  void findByUserId_returnsSavedProfile() {
    UUID userId = UUID.randomUUID();
    careerProfileRepository.save(new CareerProfile(userId));

    Optional<CareerProfile> found = careerProfileRepository.findByUserId(userId);

    assertThat(found).isPresent();
    assertThat(found.get().getUserId()).isEqualTo(userId);
  }

  @Test
  @DisplayName("patch - 온보딩 자동 저장을 위해 일부 필드만 갱신하고 나머지는 보존한다")
  void patch_updatesOnlyProvidedFields() {
    UUID userId = UUID.randomUUID();
    CareerProfile profile = careerProfileRepository.save(new CareerProfile(userId));
    profile.patch("DEVELOPER", 5, "SEOUL_GANGNAM", "FULL_TIME", "MEDIUM");
    careerProfileRepository.saveAndFlush(profile);

    profile.patch(null, 7, null, null, null);
    careerProfileRepository.saveAndFlush(profile);

    CareerProfile reloaded = careerProfileRepository.findByUserId(userId).orElseThrow();
    assertThat(reloaded.getYearsOfExperience()).isEqualTo(7);
    assertThat(reloaded.getJobFamily()).isEqualTo("DEVELOPER");
    assertThat(reloaded.getRegion()).isEqualTo("SEOUL_GANGNAM");
  }

  @Test
  @DisplayName("신규 프로필은 전 필드가 NULL 이다 — 온보딩 중 부분 저장을 허용한다")
  void newProfile_hasAllNullFields() {
    UUID userId = UUID.randomUUID();
    careerProfileRepository.saveAndFlush(new CareerProfile(userId));

    CareerProfile reloaded = careerProfileRepository.findByUserId(userId).orElseThrow();
    assertThat(reloaded.getJobFamily()).isNull();
    assertThat(reloaded.getYearsOfExperience()).isNull();
  }
}
