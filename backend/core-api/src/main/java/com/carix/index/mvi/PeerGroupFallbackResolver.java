package com.carix.index.mvi;

import com.carix.config.IndexThresholdProperties;
import com.carix.index.dto.SourceCoverage;
import com.carix.market.MarketAggregate;
import com.carix.market.MarketAggregateRepository;
import com.carix.profile.domain.CareerProfile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 동료집단 완화 알고리즘 해석기 (Peer Group Fallback Resolver).
 *
 * <p>[도메인 정의서 §2]에 정의된 5단계 완화 계층 구조(Fallback Hierarchy)를 구현합니다. 특정 세부 조건(직무 + 지역 + 경력 + 고용형태 +
 * 회사규모)에서 채용공고 표본 수(\(N\))가 최소 기준(\(N \ge 10\)) 미만으로 떨어질 때, 단계적으로 조건을 완화하여 충분한 표본을 확보합니다.
 *
 * <h3>완화 계층 구조</h3>
 *
 * <ul>
 *   <li><b>Level 1 (완전 일치):</b> 직무 + 상세구 + 경력구간 + 고용형태 + 회사규모
 *   <li><b>Level 2 (회사규모 해제):</b> 회사규모 조건 해제 ({@code company_size IS NULL} 전체 롤업 명시 선택)
 *   <li><b>Level 3 (지역 확장):</b> 상세구 \(\rightarrow\) 시/도 광역 단위로 확장 (예: 서울 강남구 \(\rightarrow\) 서울특별시)
 *   <li><b>Level 4 (경력구간 확장):</b> 인접 경력구간을 포괄 (예: MID \(\rightarrow\) JUNIOR, MID, SENIOR 전체)
 *   <li><b>Level 5 (고용형태 해제):</b> 고용형태 조건 해제 ({@code employment_type IS NULL} 전체 롤업 명시 선택)
 * </ul>
 *
 * <p><b>원천별 독립 수행:</b> 시장 집계는 원천별(JOBKOREA, SARAMIN)로 저장되므로, 완화 또한 원천별로 독립적으로 수행되어 각기 다른 완화 레벨에 안착할
 * 수 있습니다.
 */
@Component
@RequiredArgsConstructor
public class PeerGroupFallbackResolver {

  /** 최소 동료집단 표본 수 기준 (N >= 10) */
  // TODO: [Member B] 운영 피드백 및 데이터 희소성에 따라 IndexThresholdProperties 연동 검토 (도메인 정의서 §2.3)
  public static final int MIN_SAMPLE_SIZE = 10;

  public static final String BRACKET_ENTRY = "ENTRY";
  public static final String BRACKET_JUNIOR = "JUNIOR";
  public static final String BRACKET_MID = "MID";
  public static final String BRACKET_SENIOR = "SENIOR";
  public static final String BRACKET_LEAD = "LEAD";
  public static final String BRACKET_ALL = "ALL";

  /** 6구간 순서 (ENTRY ~ LEAD). 공식 임금통계(wage_stat_observation) 및 경력 사다리(careerLadder)에서 사용한다. */
  public static final List<String> BRACKET_6_ORDER =
      List.of(BRACKET_ENTRY, BRACKET_JUNIOR, BRACKET_MID, BRACKET_SENIOR, BRACKET_LEAD);

  /** 경력 3구간 순서 (채용공고 시장 집계와 동료집단 완화가 이 순서를 쓴다). */
  public static final List<String> BRACKET_ORDER =
      List.of(BRACKET_JUNIOR, BRACKET_MID, BRACKET_SENIOR);

  private final MarketAggregateRepository marketAggregateRepository;

  @Autowired(required = false)
  private IndexThresholdProperties thresholds;

  private int minSampleSize() {
    return thresholds == null ? MIN_SAMPLE_SIZE : thresholds.getPeerGroupMinSampleSize();
  }

  /** 동료집단 완화 알고리즘을 수행합니다 (소스 미지정 또는 ALL 지정 시 JOBKOREA -> SARAMIN 순으로 탐색). */
  public ResolvedPeerGroup resolve(String source, String baseYearMonth, CareerProfile profile) {
    if (source == null || "ALL".equalsIgnoreCase(source)) {
      ResolvedPeerGroup jk = resolveForSource("JOBKOREA", profile, baseYearMonth);
      if (jk.isSuccessful()) {
        return jk;
      }
      ResolvedPeerGroup saramin = resolveForSource("SARAMIN", profile, baseYearMonth);
      return saramin.isSuccessful() ? saramin : jk;
    }
    return resolveForSource(source, profile, baseYearMonth);
  }

  /**
   * 단일 원천에 대해 5단계 완화 알고리즘을 수행하여 최적의 동료집단 집계 행 및 커버리지 정보를 도출합니다.
   *
   * @param source 플랫폼 원천 (JOBKOREA, SARAMIN)
   * @param profile 사용자 커리어 프로필
   * @param baseYearMonth 기준년월 (YYYYMM)
   * @return 완화 해석 결과 (성공 시 안착 레벨 및 집계 행 포함, 표본 부족 시 실패 상태)
   */
  public ResolvedPeerGroup resolveForSource(
      String source, CareerProfile profile, String baseYearMonth) {

    String jobFamily = defaultString(profile.getJobFamily(), "DEVELOPER");
    int years = profile.getYearsOfExperience() != null ? profile.getYearsOfExperience() : 3;
    String bracket = mapExperienceBracket(years);
    String detailRegion = defaultString(profile.getRegion(), "서울 강남구");
    String broadRegion = extractBroadRegion(detailRegion);
    String employmentType = defaultString(profile.getEmploymentType(), "FULL_TIME");
    String companySize = profile.getCompanySize(); // null 가능
    int minSampleSize = minSampleSize();

    // Level 1: 완전 일치 (직무 + 상세구 + 경력구간 + 고용형태 + 회사규모)
    if (companySize != null && !companySize.isBlank()) {
      Optional<MarketAggregate> l1Agg =
          marketAggregateRepository.findPeerGroupAggregate(
              source, baseYearMonth, jobFamily, detailRegion, bracket, employmentType, companySize);
      if (l1Agg.isPresent() && l1Agg.get().getPostingCount() >= minSampleSize) {
        return buildResolvedResult(
            source, 1, l1Agg.get(), detailRegion, List.of(bracket), employmentType, companySize);
      }
    }

    // Level 2: 회사 규모 조건 해제 (company_size IS NULL 롤업 행)
    Optional<MarketAggregate> l2Agg =
        marketAggregateRepository.findPeerGroupAggregate(
            source, baseYearMonth, jobFamily, detailRegion, bracket, employmentType, null);
    if (l2Agg.isPresent() && l2Agg.get().getPostingCount() >= minSampleSize) {
      return buildResolvedResult(
          source, 2, l2Agg.get(), detailRegion, List.of(bracket), employmentType, null);
    }

    // Level 3: 지역 범위 확장 (상세구 -> 시/도 광역)
    Optional<MarketAggregate> l3Agg =
        marketAggregateRepository.findPeerGroupAggregate(
            source, baseYearMonth, jobFamily, broadRegion, bracket, employmentType, null);
    if (l3Agg.isPresent() && l3Agg.get().getPostingCount() >= minSampleSize) {
      return buildResolvedResult(
          source, 3, l3Agg.get(), broadRegion, List.of(bracket), employmentType, null);
    }

    // Level 4: 경력 구간 확장 (인접 브래킷 추가)
    List<String> expandedBrackets = getAdjacentBrackets(bracket);
    List<MarketAggregate> l4Aggs =
        marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            source, baseYearMonth, jobFamily, broadRegion, expandedBrackets, employmentType, null);
    int l4TotalCount = l4Aggs.stream().mapToInt(MarketAggregate::getPostingCount).sum();
    if (!l4Aggs.isEmpty() && l4TotalCount >= minSampleSize) {
      return buildCompositeResolvedResult(
          source, 4, l4Aggs, broadRegion, expandedBrackets, employmentType, null);
    }

    // Level 5: 고용 형태 조건 해제 (employment_type IS NULL 롤업 행)
    List<MarketAggregate> l5Aggs =
        marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            source, baseYearMonth, jobFamily, broadRegion, expandedBrackets, null, null);
    int l5TotalCount = l5Aggs.stream().mapToInt(MarketAggregate::getPostingCount).sum();
    if (!l5Aggs.isEmpty() && l5TotalCount >= minSampleSize) {
      return buildCompositeResolvedResult(
          source, 5, l5Aggs, broadRegion, expandedBrackets, null, null);
    }

    // 최종 완화 실패 (모든 단계에서 N < 10)
    int fallbackSampleCount =
        !l5Aggs.isEmpty() ? l5TotalCount : (!l4Aggs.isEmpty() ? l4TotalCount : 0);
    return ResolvedPeerGroup.builder()
        .source(source)
        .successful(false)
        .fallbackLevel(5)
        .sampleCount(fallbackSampleCount)
        .coverage(
            new SourceCoverage(
                source,
                5,
                fallbackSampleCount,
                false,
                formatConditionText(broadRegion, expandedBrackets, null, null),
                BigDecimal.valueOf(100.00)))
        .jobFamily(jobFamily)
        .region(broadRegion)
        .brackets(expandedBrackets)
        .employmentType(null)
        .companySize(null)
        .build();
  }

  /** 단일 집계 행에 대한 성공적인 해석 결과 객체를 생성합니다. */
  private ResolvedPeerGroup buildResolvedResult(
      String source,
      int level,
      MarketAggregate aggregate,
      String region,
      List<String> brackets,
      String employmentType,
      String companySize) {

    String conditionText = formatConditionText(region, brackets, employmentType, companySize);
    SourceCoverage coverage =
        new SourceCoverage(
            source,
            level,
            aggregate.getPostingCount(),
            true,
            conditionText,
            aggregate.getSalaryMissingRatePercent());

    return ResolvedPeerGroup.builder()
        .source(source)
        .successful(true)
        .fallbackLevel(level)
        .sampleCount(aggregate.getPostingCount())
        .coverage(coverage)
        .jobFamily(aggregate.getJobFamily())
        .region(region)
        .brackets(brackets)
        .employmentType(employmentType)
        .companySize(companySize)
        .build();
  }

  /** 복수 집계 행(Level 4, 5 브래킷 통합)에 대한 합성 해석 결과 객체를 생성합니다. */
  private ResolvedPeerGroup buildCompositeResolvedResult(
      String source,
      int level,
      List<MarketAggregate> aggregates,
      String region,
      List<String> brackets,
      String employmentType,
      String companySize) {

    int totalPostings = aggregates.stream().mapToInt(MarketAggregate::getPostingCount).sum();

    // 결측률 가중 평균 계산
    BigDecimal weightedMissingSum = BigDecimal.ZERO;
    for (MarketAggregate m : aggregates) {
      weightedMissingSum =
          weightedMissingSum.add(
              m.getSalaryMissingRatePercent().multiply(BigDecimal.valueOf(m.getPostingCount())));
    }
    BigDecimal avgMissingRate =
        totalPostings > 0
            ? weightedMissingSum.divide(BigDecimal.valueOf(totalPostings), 2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(100.00);

    String conditionText = formatConditionText(region, brackets, employmentType, companySize);
    SourceCoverage coverage =
        new SourceCoverage(source, level, totalPostings, true, conditionText, avgMissingRate);

    return ResolvedPeerGroup.builder()
        .source(source)
        .successful(true)
        .fallbackLevel(level)
        .sampleCount(totalPostings)
        .coverage(coverage)
        .jobFamily(aggregates.get(0).getJobFamily())
        .region(region)
        .brackets(brackets)
        .employmentType(employmentType)
        .companySize(companySize)
        .build();
  }

  /** 화면 및 메타데이터에 노출할 완화 조건 텍스트를 포맷팅합니다. 예: "서울 전체 / 경력 4~7년(MID) / 회사규모 무관" */
  public String formatConditionText(
      String region, Collection<String> brackets, String employmentType, String companySize) {

    String regionText = region.contains("구") ? region : region + " 전체";
    String bracketText = formatBracketText(brackets);
    String sizeText = companySize != null ? companySize : "회사규모 무관";
    String typeText = employmentType != null ? employmentType : "고용형태 무관";

    return String.format("%s / %s / %s / %s", regionText, bracketText, sizeText, typeText);
  }

  /**
   * 브래킷 묶음을 화면 문구로 만듭니다. 세 구간이 다 모였을 때만 `경력 전체 구간` 입니다 — 인접 확장으로 두 구간이 된 경우를 전체로 표기하면 화면이 실제보다 넓은
   * 비교군을 본 것처럼 말하게 됩니다.
   */
  private String formatBracketText(Collection<String> brackets) {
    if (brackets == null || brackets.isEmpty()) {
      return "경력 전체 구간";
    }
    if (brackets.size() == 1) {
      String b = brackets.iterator().next();
      return b + " (" + formatBracketYears(b) + ")";
    }
    List<String> ordered = BRACKET_6_ORDER.stream().filter(brackets::contains).toList();
    if (ordered.size() >= BRACKET_6_ORDER.size() || ordered.containsAll(BRACKET_ORDER)) {
      return "경력 전체 구간";
    }
    if (ordered.isEmpty()) {
      return String.join("·", brackets);
    }
    return String.join("·", ordered) + " (" + formatBracketRange(ordered) + ")";
  }

  /** 인접 확장으로 묶인 구간의 연차 범위. 첫 브래킷의 하한과 마지막 브래킷의 상한을 잇습니다. */
  private String formatBracketRange(List<String> ordered) {
    String lowerBound =
        switch (ordered.getFirst()) {
          case BRACKET_ENTRY, BRACKET_JUNIOR -> "0";
          case BRACKET_MID -> "4";
          case BRACKET_SENIOR -> "8";
          default -> "12";
        };
    return switch (ordered.getLast()) {
      case BRACKET_JUNIOR -> lowerBound + "~3년";
      case BRACKET_MID -> lowerBound + "~7년";
      default -> lowerBound + "년+";
    };
  }

  private String formatBracketYears(String bracket) {
    return switch (bracket) {
      case BRACKET_ENTRY -> "0~1년";
      case BRACKET_JUNIOR -> "0~3년";
      case BRACKET_MID -> "4~7년";
      case BRACKET_SENIOR -> "8년+";
      case BRACKET_LEAD -> "12년+";
      default -> bracket;
    };
  }

  /**
   * 총 경력 년수를 6구간 브래킷(ENTRY, JUNIOR, MID, SENIOR, LEAD)으로 매핑합니다.
   *
   * <p>KOSIS 공식 임금통계 기준선(wage_stat_observation) 및 경력 사다리(careerLadder)에서 사용됩니다.
   */
  public String mapExperienceBracket6(int yearsOfExperience) {
    if (yearsOfExperience <= 1) {
      return BRACKET_ENTRY;
    } else if (yearsOfExperience <= 3) {
      return BRACKET_JUNIOR;
    } else if (yearsOfExperience <= 7) {
      return BRACKET_MID;
    } else if (yearsOfExperience <= 11) {
      return BRACKET_SENIOR;
    } else {
      return BRACKET_LEAD;
    }
  }

  /**
   * 6구간 브래킷을 채용공고 동료집단 완화용 3구간 브래킷(JUNIOR, MID, SENIOR)으로 롤업합니다.
   *
   * <p>ENTRY -> JUNIOR, LEAD -> SENIOR 로 매핑하여 기존 market_aggregate 집계 행과 100% 호환됩니다.
   */
  public String to3TierBracket(String bracket6) {
    if (bracket6 == null) {
      return BRACKET_JUNIOR;
    }
    return switch (bracket6) {
      case BRACKET_ENTRY, BRACKET_JUNIOR -> BRACKET_JUNIOR;
      case BRACKET_MID -> BRACKET_MID;
      case BRACKET_SENIOR, BRACKET_LEAD -> BRACKET_SENIOR;
      default -> bracket6;
    };
  }

  /**
   * 총 경력 년수를 JUNIOR, MID, SENIOR 3구간 브래킷으로 매핑합니다.
   *
   * <p>채용공고 시장 사전 집계(market_aggregate) 및 동료집단 완화 알고리즘에서 사용됩니다.
   */
  public String mapExperienceBracket(int yearsOfExperience) {
    if (yearsOfExperience <= 3) {
      return BRACKET_JUNIOR;
    } else if (yearsOfExperience <= 7) {
      return BRACKET_MID;
    } else {
      return BRACKET_SENIOR;
    }
  }

  /**
   * Level 4 완화 시 인접 경력 브래킷 목록을 반환합니다 (도메인 정의서 §2.1).
   *
   * <p>양옆 한 칸까지만 넓힙니다. {@code MID} 는 양쪽이 모두 인접이라 결과가 세 구간이 되지만, {@code JUNIOR} 에 {@code SENIOR} 를
   * 섞지는 않습니다 — 0~3년 사용자의 동료집단에 8년+ 공고가 들어오면 수요율과 연봉 분포가 함께 왜곡됩니다. 이 단계에서 경력 차원을 통째로 버리면 남은 Level
   * 5(고용형태 해제)가 사다리의 마지막 칸 구실을 못 합니다.
   */
  public List<String> getAdjacentBrackets(String bracket) {
    return switch (bracket) {
      case "JUNIOR" -> List.of("JUNIOR", "MID");
      case "SENIOR" -> List.of("MID", "SENIOR");
      default -> BRACKET_ORDER;
    };
  }

  /** 상세 구 단위 지역명에서 시/도 광역 지역명을 추출합니다. 예: "서울 강남구" -> "서울특별시", "경기 성남시" -> "경기도" */
  public String extractBroadRegion(String detailRegion) {
    if (detailRegion == null || detailRegion.isBlank()) {
      return "서울특별시";
    }
    String trimmed = detailRegion.trim();
    if (trimmed.startsWith("서울")) {
      return "서울특별시";
    } else if (trimmed.startsWith("경기")) {
      return "경기도";
    } else if (trimmed.startsWith("인천")) {
      return "인천광역시";
    } else if (trimmed.startsWith("부산")) {
      return "부산광역시";
    } else if (trimmed.startsWith("대구")) {
      return "대구광역시";
    } else if (trimmed.startsWith("대전")) {
      return "대전광역시";
    } else if (trimmed.startsWith("광주")) {
      return "광주광역시";
    }
    return trimmed;
  }

  private String defaultString(String val, String def) {
    return (val != null && !val.isBlank()) ? val : def;
  }

  /** 동료집단 완화 해석 결과 DTO. */
  @Getter
  @Builder
  public static class ResolvedPeerGroup {
    private final String source;
    private final boolean successful;
    private final int fallbackLevel;
    private final int sampleCount;
    private final SourceCoverage coverage;
    private final String jobFamily;
    private final String region;
    private final List<String> brackets;
    private final String employmentType;
    private final String companySize;
  }
}
