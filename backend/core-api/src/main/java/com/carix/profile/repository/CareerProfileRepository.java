package com.carix.profile.repository;

import com.carix.profile.domain.CareerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 커리어 프로필(core.career_profile) 조회 리포지토리. */
@Repository
public interface CareerProfileRepository extends JpaRepository<CareerProfile, UUID> {

  /**
   * 사용자 ID로 커리어 프로필을 조회합니다.
   *
   * @param userId 사용자 식별자
   * @return 커리어 프로필 (Optional)
   */
  Optional<CareerProfile> findByUserId(UUID userId);
}
