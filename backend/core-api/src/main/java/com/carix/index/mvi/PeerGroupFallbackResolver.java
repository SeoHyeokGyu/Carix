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
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 시장가치 지수(MVI) 동료집단 해석기.
 *
 * <p><b>실재하는 차원은 직무 × 경력뿐이다.</b> collector 는 {@code market_aggregate} 를 직무 × 경력 구간으로만 나누고 지역·고용형태·
 * 회사규모는 {@code ALL} 로 집계한다. 그래서 이 해석기는 세 차원을 {@code ALL} 로 명시해 조회하고, 비교 조건 문구도 프로필이 아니라 실제 조회
 * 조건(「전국 / 경력 / 회사규모 무관 / 고용형태 무관」)으로 쓴다 — 기능 명세 §4.2, US-D1 「실제 적용된 비교 조건 명시」.
 *
 * <h3>완화 2단계</h3>
 *
 * <ul>
 *   <li><b>Level 1:</b> 본인 경력 구간
 *   <li><b>Level 2:</b> 인접 경력 구간까지 확장
 * </ul>
 *
 * <p>경력은 3구간(JUNIOR 0~3·MID 4~7·SENIOR 8+)으로 판정하고 collector 6구간 행을 합산한다 — JUNIOR = ENTRY+JUNIOR,
 * SENIOR = SENIOR+LEAD. 6구간은 임금통계·경력 사다리와 맞춰 둔 것이라 collector 쪽을 바꾸지 않는다.
 *
 * <p><b>원천별 독립 수행:</b> 시장 집계는 원천별(JOBKOREA, SARAMIN, WANTED, JUMPIT)로 저장되므로 완화도 원천별로 따로 안착한다.
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

  /** 집계 차원 중 collector 가 나누지 않는 것(지역·고용형태·회사규모)의 값. */
  public static final String DIMENSION_ALL = "ALL";

  /**
   * MVI 스킬 수요율의 동료집단 — 직무 × 경력 전체(지역·고용형태·규모 무관).
   *
   * <p>수요율은 경력 구간으로 나누지 않는다. 무작위 표본(잡코리아 월 250건)을 경력 넷으로 쪼개면 스킬별 건수가 한 자릿수로 떨어져 지수 잡음(±30~40%)이 판정
   * 기준선 간격(5~20)을 넘는다. 「이 스킬의 수요가 늘었나」는 경력 구간마다 크게 다르지 않고, 경력이 갈라야 하는 건 연봉 기준선이다 — 그쪽은 {@link
   * WageBaselineResolver} 가 경력 구간별로 잡는다.
   *
   * @param source 플랫폼 원천 (JOBKOREA, SARAMIN, WANTED, JUMPIT)
   * @param profile 사용자 커리어 프로필 — 직무만 쓴다
   * @param baseYearMonth 기준년월 (YYYYMM)
   * @return 해석 결과 (표본 부족 시 실패 상태)
   */
  public ResolvedPeerGroup resolveForSource(
      String source, CareerProfile profile, String baseYearMonth) {

    String jobFamily = defaultString(profile.getJobFamily(), "DEVELOPER");
    List<String> allTiers = List.of(BRACKET_ALL);
    List<MarketAggregate> rows =
        marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            source,
            baseYearMonth,
            jobFamily,
            DIMENSION_ALL,
            allTiers,
            DIMENSION_ALL,
            DIMENSION_ALL);
    if (sum(rows) >= minSampleSize()) {
      return buildCompositeResolvedResult(source, 1, rows, allTiers);
    }

    int sampleCount = sum(rows);
    return ResolvedPeerGroup.builder()
        .source(source)
        .successful(false)
        .fallbackLevel(1)
        .sampleCount(sampleCount)
        .coverage(
            new SourceCoverage(
                source,
                1,
                sampleCount,
                false,
                formatNationwideConditionText(allTiers),
                BigDecimal.valueOf(100.00)))
        .jobFamily(jobFamily)
        .region(DIMENSION_ALL)
        .brackets(allTiers)
        .employmentType(DIMENSION_ALL)
        .companySize(DIMENSION_ALL)
        .build();
  }

  private static int sum(List<MarketAggregate> rows) {
    return rows.stream().mapToInt(MarketAggregate::getPostingCount).sum();
  }

  /**
   * MVI 동료집단 비교 조건 문구. 실제 조회 조건이라 지역·규모·고용형태는 늘 무관이다. 경력은 연차로만 쓴다 — 화면이 이 문구를 그대로 보이므로 {@code MID}
   * 같은 원시 enum 을 섞지 않는다.
   */
  public String formatNationwideConditionText(List<String> tiers) {
    return String.format("전국 / %s / 회사규모 무관 / 고용형태 무관", formatTierYears(tiers));
  }

  private String formatTierYears(List<String> tiers) {
    List<String> ordered = BRACKET_ORDER.stream().filter(tiers::contains).toList();
    if (ordered.isEmpty() || ordered.size() == BRACKET_ORDER.size()) {
      return "경력 전체";
    }
    if (ordered.size() == 1) {
      return formatBracketYears(ordered.getFirst());
    }
    return formatBracketRange(ordered);
  }

  /** 합산한 집계 행에 대한 해석 결과. 비교 조건과 차원 값은 실제 조회 조건 그대로다. */
  private ResolvedPeerGroup buildCompositeResolvedResult(
      String source, int level, List<MarketAggregate> aggregates, List<String> tiers) {

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

    SourceCoverage coverage =
        new SourceCoverage(
            source,
            level,
            totalPostings,
            true,
            formatNationwideConditionText(tiers),
            avgMissingRate);

    return ResolvedPeerGroup.builder()
        .source(source)
        .successful(true)
        .fallbackLevel(level)
        .sampleCount(totalPostings)
        .coverage(coverage)
        .jobFamily(aggregates.get(0).getJobFamily())
        .region(DIMENSION_ALL)
        .brackets(tiers)
        .employmentType(DIMENSION_ALL)
        .companySize(DIMENSION_ALL)
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
