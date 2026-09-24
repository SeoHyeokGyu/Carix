package com.carix.peergroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.profile.domain.CareerProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeerSalaryDistributionResolverTest {

  private JobPostingRepository jobPostingRepository;
  private PeerGroupFallbackResolver peerGroupFallbackResolver;
  private PeerSalaryDistributionResolver resolver;

  @BeforeEach
  void setUp() {
    jobPostingRepository = mock(JobPostingRepository.class);
    peerGroupFallbackResolver =
        new PeerGroupFallbackResolver(mock(com.carix.market.MarketAggregateRepository.class));
    resolver = new PeerSalaryDistributionResolver(jobPostingRepository, peerGroupFallbackResolver);
  }

  @Test
  @DisplayName("resolve - Level 1 완전 일치로 안착 (회사규모 포함)")
  void resolve_level1Match() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "LARGE");

    List<JobPosting> postings = createPostings(12, 60000000L);
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), eq("LARGE"), eq(4), eq(7)))
        .willReturn(postings);

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution).isNotNull();
    assertThat(distribution.fallbackLevel()).isEqualTo(1);
    assertThat(distribution.hasSufficientPeers()).isTrue();
    assertThat(distribution.peerSampleSize()).isEqualTo(12);
    assertThat(distribution.disclosedSalaries()).hasSize(12);
    assertThat(distribution.appliedConditions()).contains("서울 강남구 / 경력 MID / LARGE");
  }

  @Test
  @DisplayName("resolve - companySize가 null이거나 빈칸이면 Level 1을 건너뛰고 Level 2로 안착")
  void resolve_skipLevel1WhenCompanySizeBlank() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "");

    List<JobPosting> postings = createPostings(10, 55000000L);
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), isNull(), eq(4), eq(7)))
        .willReturn(postings);

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution).isNotNull();
    assertThat(distribution.fallbackLevel()).isEqualTo(2);
    assertThat(distribution.hasSufficientPeers()).isTrue();
    assertThat(distribution.appliedConditions()).contains("회사규모 무관");
  }

  @Test
  @DisplayName("resolve - 프로필 필드가 null인 경우 기본값 및 지역명 접두사(공백 없는 지역) 처리")
  void resolve_defaultProfileValuesAndSingleWordRegion() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch(null, null, "서울특별시", null, null);

    List<JobPosting> postings = createPostings(10, 50000000L);
    // yearsOfExperience == null -> 3 -> JUNIOR -> [0, 3]
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울특별시"), eq("FULL_TIME"), isNull(), eq(0), eq(3)))
        .willReturn(postings);

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution).isNotNull();
    assertThat(distribution.fallbackLevel()).isEqualTo(2);
    assertThat(distribution.hasSufficientPeers()).isTrue();
  }

  @Test
  @DisplayName("resolve - 모든 단계 표본 부족 시 Level 5 결과 반환")
  void resolve_insufficientSamplesAcrossAllLevels() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 10, "경기 성남시", "FULL_TIME", null);

    // SENIOR -> [8, 99], adjacent -> MID·SENIOR [4, 99]
    given(
            jobPostingRepository.findPeerGroupCandidates(
                anyString(), anyString(), any(), any(), anyInt(), anyInt()))
        .willReturn(createPostings(3, 80000000L));

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution).isNotNull();
    assertThat(distribution.fallbackLevel()).isEqualTo(5);
    assertThat(distribution.hasSufficientPeers()).isFalse();
    assertThat(distribution.peerSampleSize()).isEqualTo(3);
  }

  private List<JobPosting> createPostings(int count, long salary) {
    List<JobPosting> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      JobPosting posting =
          JobPosting.builder()
              .id(UUID.randomUUID())
              .region("서울 강남구")
              .employmentType("FULL_TIME")
              .salaryMin(salary)
              .salaryMax(salary)
              .dedupGroupId(UUID.randomUUID())
              .build();
      list.add(posting);
    }
    return list;
  }
}
