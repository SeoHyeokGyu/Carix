package com.carix.peergroup;

import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.profile.domain.CareerProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원 공고({@code job_posting}) 위에서 동료집단 5단계 완화를 돌려 연봉 분포를 만든다.
 *
 * <p>{@code market_aggregate} 를 쓰지 않는 이유는 그쪽이 원천별로 5수치만 담아 결합 분포와 백분위를 재구성할 수 없어서다. 대신 {@code
 * dedup_group_id} 중복 제거와 유효 공개연봉 필터를 여기서 직접 건다 — 정의는 collector 의 {@code MarketAggregateService} 와
 * 같다.
 *
 * <p>단계 정의는 {@link PeerGroupFallbackResolver} 와 같고 조회 대상만 다르다.
 */
@Service
@RequiredArgsConstructor
public class PeerSalaryDistributionResolver {

  private final JobPostingRepository jobPostingRepository;
  private final PeerGroupFallbackResolver peerGroupFallbackResolver;

  /**
   * 프로필의 동료집단 분포. 어느 단계에서도 최소 표본을 못 채우면 가장 넓은 단계의 결과를 그대로 돌려준다 — 호출자가 {@link
   * PeerSalaryDistribution#hasSufficientPeers()} 로 판단한다.
   *
   * <p>지역은 {@code extractBroadRegion} 이 아니라 첫 토큰을 접두사로 쓴다. 그 메서드는 {@code market_aggregate.region} 의
   * 정규화된 값({@code "서울특별시"})을 만드는데, 원 공고의 {@code region} 은 {@code "서울 강남구"} 형태라 접두사가 맞아야 걸린다.
   */
  @Transactional(readOnly = true)
  public PeerSalaryDistribution resolve(CareerProfile profile) {
    String jobFamily = orDefault(profile.getJobFamily(), "DEVELOPER");
    int years = profile.getYearsOfExperience() != null ? profile.getYearsOfExperience() : 3;
    String bracket = peerGroupFallbackResolver.mapExperienceBracket(years);
    String detailRegion = orDefault(profile.getRegion(), "서울 강남구");
    String broadPrefix = broadRegionPrefix(detailRegion);
    String employmentType = orDefault(profile.getEmploymentType(), "FULL_TIME");
    String companySize = profile.getCompanySize();

    int[] range = bracketYearRange(bracket);
    int[] widened = bracketYearRange(peerGroupFallbackResolver.getAdjacentBrackets(bracket));

    List<Resolved> ladder = new ArrayList<>();
    if (companySize != null && !companySize.isBlank()) {
      ladder.add(
          attempt(
              1,
              jobFamily,
              detailRegion,
              employmentType,
              companySize,
              range,
              detailRegion,
              bracket));
    }
    ladder.add(
        attempt(2, jobFamily, detailRegion, employmentType, null, range, detailRegion, bracket));
    ladder.add(
        attempt(3, jobFamily, broadPrefix, employmentType, null, range, broadPrefix, bracket));
    ladder.add(
        attempt(
            4, jobFamily, broadPrefix, employmentType, null, widened, broadPrefix, bracket + "±1"));
    ladder.add(
        attempt(5, jobFamily, broadPrefix, null, null, widened, broadPrefix, bracket + "±1"));

    for (Resolved candidate : ladder) {
      if (candidate.postings().size() >= PeerGroupFallbackResolver.MIN_SAMPLE_SIZE) {
        return toDistribution(candidate);
      }
    }
    return toDistribution(ladder.get(ladder.size() - 1));
  }

  private PeerSalaryDistribution toDistribution(Resolved resolved) {
    return new PeerSalaryDistribution(
        disclosedSalaries(resolved.postings()),
        resolved.postings().size(),
        resolved.level(),
        resolved.appliedConditions());
  }

  private Resolved attempt(
      int level,
      String jobFamily,
      String regionPrefix,
      String employmentType,
      String companySize,
      int[] yearRange,
      String regionLabel,
      String bracketLabel) {

    List<JobPosting> candidates =
        jobPostingRepository.findPeerGroupCandidates(
            jobFamily, regionPrefix, employmentType, companySize, yearRange[0], yearRange[1]);

    return new Resolved(
        level,
        dedupe(candidates),
        "%s / 경력 %s / %s".formatted(regionLabel, bracketLabel, companySizeLabel(companySize)));
  }

  /** {@code dedup_group_id} 당 대표 1건만 남긴다 (collector 와 같은 규약 — 먼저 만난 행이 대표다). */
  private List<JobPosting> dedupe(List<JobPosting> postings) {
    Map<UUID, JobPosting> byGroup = new LinkedHashMap<>();
    for (JobPosting posting : postings) {
      byGroup.putIfAbsent(posting.dedupKey(), posting);
    }
    return new ArrayList<>(byGroup.values());
  }

  private List<Long> disclosedSalaries(List<JobPosting> postings) {
    List<Long> values = new ArrayList<>();
    for (JobPosting posting : postings) {
      Long salary = posting.disclosedSalary();
      if (salary != null) {
        values.add(salary);
      }
    }
    Collections.sort(values);
    return values;
  }

  /** 브래킷의 연 단위 범위. {@link PeerGroupFallbackResolver#mapExperienceBracket} 의 경계와 같다. */
  private int[] bracketYearRange(String bracket) {
    return switch (bracket) {
      case "JUNIOR" -> new int[] {0, 3};
      case "SENIOR" -> new int[] {8, 99};
      default -> new int[] {4, 7};
    };
  }

  private int[] bracketYearRange(List<String> brackets) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (String bracket : brackets) {
      int[] range = bracketYearRange(bracket);
      min = Math.min(min, range[0]);
      max = Math.max(max, range[1]);
    }
    return new int[] {min, max};
  }

  private String broadRegionPrefix(String detailRegion) {
    String trimmed = detailRegion.trim();
    int space = trimmed.indexOf(' ');
    return space > 0 ? trimmed.substring(0, space) : trimmed;
  }

  private String companySizeLabel(String companySize) {
    return companySize == null || companySize.isBlank() ? "회사규모 무관" : companySize;
  }

  private String orDefault(String value, String fallback) {
    return (value != null && !value.isBlank()) ? value : fallback;
  }

  private record Resolved(int level, List<JobPosting> postings, String appliedConditions) {}
}
