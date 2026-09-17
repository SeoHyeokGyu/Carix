package com.carix.wagestat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KOSIS 국가 공식 임금 통계를 수집·보간하여 {@code core.wage_stat_observation} 테이블에 적재하는 서비스.
 *
 * <p>본 서비스는 통계청 고용형태별근로실태조사(DT_118N_PAYM39) 24개 임금계층 누적분포를 바탕으로, 동일 연도/직군/경력구간에 대해 2가지 기준선 데이터를
 * 쌍(Pair)으로 생성 및 관리합니다:
 *
 * <ul>
 *   <li><b>{@link WageDefinition#ANNUAL_GROSS_MEDIAN}:</b> 연간 세전 총액 중위임금 (기본급 + 상여금 포함)
 *   <li><b>{@link WageDefinition#ANNUAL_BASE_MEDIAN}:</b> 연간 정액급여 중위임금 (기본급만 포함, 상여금/초과급여 제외 -
 *       83.5% 계수 적용)
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WageStatIngestService {

  private static final String BASE_PERIOD_DEFAULT = "2025";
  private static final String REGION_ALL = "ALL";

  /** 연도 파라미터는 네 자리 숫자만 받는다. base_period 가 연 단위 공시라 그 형상과 같다. */
  private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");

  /** 통계청 고용형태별근로실태조사 정기 공시 월 (매년 8월 기준 빈티지). */
  private static final String STAT_VINTAGE_MONTH_SUFFIX = "08";

  /** 만원 단위 반올림 단위. */
  private static final double ROUNDING_UNIT_TEN_THOUSAND = 10_000.0;

  /** 브래킷별 Fallback 누락 시 적용되는 기본 연간 중위연봉 (4,500만 원). */
  private static final long DEFAULT_MEDIAN_FALLBACK = 45_000_000L;

  /**
   * 통계청 고용형태별근로실태조사(전문가 직군) 기준 정액급여(기본급) 평균 구성 비중 계수 (83.5%).
   *
   * <p>{@link WageDefinition#ANNUAL_GROSS_MEDIAN}(총액) 통계로부터 {@link
   * WageDefinition#ANNUAL_BASE_MEDIAN}(정액급여)을 도출하는 공식 기준선 계수입니다.
   */
  private static final double BASE_SALARY_RATIO = 0.835;

  private static final List<String> JOB_FAMILIES = List.of("DEVELOPER", "DATA", "AI", "ALL");

  // KOSIS 연령 코드 -> Carix 경력 구간 (Experience Bracket) 매핑
  private static final Map<String, String> AGE_CODE_TO_EXPERIENCE_BRACKET =
      Map.of(
          "00", "ALL", // 전체
          "03", "ENTRY", // 20~24세 -> 신입
          "04", "JUNIOR", // 25~29세 -> 주니어 (1~3년)
          "05", "MID", // 30~34세 -> 미드 (4~7년)
          "06", "SENIOR", // 35~39세 -> 시니어 (8~11년)
          "07", "LEAD" // 40~44세 -> 리드/수석 (12년+)
          );

  // KOSIS API 미호출 시 안전 기본 기준선 (2025년 기준 공식 보간값 fallback)
  private static final Map<String, Long> FALLBACK_MEDIAN_BY_BRACKET =
      Map.of(
          "ALL", 46_800_000L, // 약 390만원/월 -> 4,680만원/년
          "ENTRY", 31_200_000L, // 약 260만원/월 -> 3,120만원/년
          "JUNIOR", 39_600_000L, // 약 330만원/월 -> 3,960만원/년
          "MID", 51_600_000L, // 약 430만원/월 -> 5,160만원/년
          "SENIOR", 62_400_000L, // 약 520만원/월 -> 6,240만원/년
          "LEAD", 74_400_000L // 약 620만원/월 -> 7,440만원/년
          );

  private final KosisWageStatClient kosisClient;
  private final WageStatInterpolator interpolator;
  private final WageStatObservationRepository repository;

  /**
   * KOSIS 국가 공식 임금 통계를 최신 시점 기준으로 실시간 조회하여 보간 후 DB에 Upsert 합니다.
   *
   * @return 수집 및 적재된 관측치 행 수
   */
  @Transactional
  public int ingestWageStats() {
    return ingestWageStats(null, null);
  }

  /**
   * KOSIS 국가 공식 임금 통계를 지정한 연도 구간으로 조회하여 보간 후 DB에 Upsert 합니다. (ANNUAL_GROSS_MEDIAN 및
   * ANNUAL_BASE_MEDIAN 두 기준선을 동시에 적재합니다.)
   *
   * @param startYear 수집 시작 연도 (예: "2020", null 시 최신 연도만)
   * @param endYear 수집 종료 연도 (예: "2024", null 시 startYear와 동일)
   * @return 수집 및 적재된 관측치 행 수
   */
  @Transactional
  public int ingestWageStats(String startYear, String endYear) {
    String start = validateYear(startYear, "startYear");
    String end = validateYear(endYear, "endYear");
    // 형식만 보는 검증은 역순 범위를 통과시킨다. 그러면 KOSIS 가 빈 응답을 주고 폴백으로
    // 떨어져 엉뚱한 연도에 상수가 쓰이는데 작업은 성공으로 보고된다.
    if (start != null && end != null && start.compareTo(end) > 0) {
      throw new IllegalArgumentException(
          "startYear 가 endYear 보다 늦습니다: %s ~ %s".formatted(start, end));
    }

    log.info(
        "[임금통계수집] KOSIS 임금 통계 수집 및 보간 작업을 시작합니다... (기간: {} ~ {})",
        start != null ? start : "최신",
        end != null ? end : (start != null ? start : "최신"));

    int ingestedCount = 0;
    for (Map.Entry<String, String> entry : AGE_CODE_TO_EXPERIENCE_BRACKET.entrySet()) {
      ingestedCount += ingestBracket(entry.getKey(), entry.getValue(), start, end);
    }

    log.info("[임금통계수집] KOSIS 임금 통계 수집 완료: 총 {}건의 관측치가 저장/갱신되었습니다.", ingestedCount);
    return ingestedCount;
  }

  private int ingestBracket(
      String ageCode, String experienceBracket, String startYear, String endYear) {
    Map<String, Map<String, Long>> yearlyDistribution =
        kosisClient.fetchWageDistributionByPeriod(ageCode, startYear, endYear);

    if (!yearlyDistribution.isEmpty()) {
      return ingestFromKosis(experienceBracket, yearlyDistribution);
    }
    return ingestFromFallback(experienceBracket, startYear, endYear);
  }

  private int ingestFromKosis(
      String experienceBracket, Map<String, Map<String, Long>> yearlyDistribution) {
    int count = 0;
    for (Map.Entry<String, Map<String, Long>> yearEntry : yearlyDistribution.entrySet()) {
      String basePeriod = yearEntry.getKey();
      long grossMedian = interpolator.calculateAnnualMedianWage(yearEntry.getValue());
      WageStatSource source = WageStatSource.KOSIS;
      // KOSIS 가 응답해도 그 해만 보간에 실패할 수 있다. 출처를 메서드 단위로 정하면
      // 상수가 KOSIS 라벨을 달고 실측으로 승격된다.
      if (grossMedian <= 0L) {
        grossMedian =
            FALLBACK_MEDIAN_BY_BRACKET.getOrDefault(experienceBracket, DEFAULT_MEDIAN_FALLBACK);
        source = WageStatSource.FALLBACK;
      }
      String statVintage = basePeriod + STAT_VINTAGE_MONTH_SUFFIX;
      count +=
          saveDualWageObservations(experienceBracket, grossMedian, basePeriod, statVintage, source);
    }
    return count;
  }

  private int ingestFromFallback(String experienceBracket, String startYear, String endYear) {
    long grossMedian =
        FALLBACK_MEDIAN_BY_BRACKET.getOrDefault(experienceBracket, DEFAULT_MEDIAN_FALLBACK);
    long baseMedian = calculateBaseMedian(grossMedian);
    log.info(
        "[임금통계수집] KOSIS 응답 부재로 기본 보간값을 사용합니다 (경력: {}, 세전총액: {}원, 정액급여: {}원)",
        experienceBracket,
        grossMedian,
        baseMedian);

    // 여러 해에 같은 상수를 뿌리지 않는다. findLatestBaseline 이 base_period DESC 라
    // 폴백의 최신 연도가 실측을 이기고, 상승률 0% 의 가짜 시계열도 여기서 생긴다.
    String basePeriod = resolveFallbackBasePeriod(startYear, endYear);
    // vintage 를 실행 시점(yyyyMM)으로 두면 stat_vintage 가 유니크 키의 일부라 달마다 좌표가
    // 달라진다. 그러면 승격 가드가 조회하는 자리에 기존 행이 없어 발화하지 않고, 매달 행이
    // 쌓이며, 그 행의 vintage 가 실측보다 커서 findLatestBaseline 이 상수를 고른다.
    // KOSIS 경로와 같은 식으로 고정해 좌표를 안정시킨다.
    String statVintage = basePeriod + STAT_VINTAGE_MONTH_SUFFIX;

    return saveDualWageObservations(
        experienceBracket, grossMedian, basePeriod, statVintage, WageStatSource.FALLBACK);
  }

  /** 폴백은 요청 범위의 마지막 연도 한 해만 쓴다. 범위가 없으면 기본 기준연도다. */
  private String resolveFallbackBasePeriod(String startYear, String endYear) {
    if (endYear != null && !endYear.isBlank()) {
      return endYear.trim();
    }
    if (startYear != null && !startYear.isBlank()) {
      return startYear.trim();
    }
    return BASE_PERIOD_DEFAULT;
  }

  /**
   * 연도 파라미터를 검증해 돌려준다. 비어 있으면 {@code null} 이다.
   *
   * <p>검증 없이 통과시키면 두 곳이 깨진다. 값이 {@code base_period} 에 그대로 저장되는데 {@code findLatestBaseline} 이 {@code
   * base_period DESC} 라 숫자가 아닌 값이 실측 연도를 이긴다. 같은 값이 KOSIS 요청 URL 에도 붙어 질의 파라미터가 섞인다.
   */
  private String validateYear(String value, String field) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (!YEAR_PATTERN.matcher(trimmed).matches()) {
      throw new IllegalArgumentException("%s 는 네 자리 연도여야 합니다: %s".formatted(field, trimmed));
    }
    return trimmed;
  }

  /** 지정된 경력구간/연도에 대해 모든 직군(JOB_FAMILIES)의 세전총액(GROSS)과 정액급여(BASE) 관측치를 저장합니다. */
  private int saveDualWageObservations(
      String experienceBracket,
      long grossMedian,
      String basePeriod,
      String statVintage,
      WageStatSource source) {
    long baseMedian = calculateBaseMedian(grossMedian);
    int savedCount = 0;

    for (String jobFamily : JOB_FAMILIES) {
      if (saveOrUpdateObservation(
          jobFamily,
          experienceBracket,
          REGION_ALL,
          WageDefinition.ANNUAL_GROSS_MEDIAN,
          grossMedian,
          basePeriod,
          statVintage,
          source)) {
        savedCount++;
      }

      if (saveOrUpdateObservation(
          jobFamily,
          experienceBracket,
          REGION_ALL,
          WageDefinition.ANNUAL_BASE_MEDIAN,
          baseMedian,
          basePeriod,
          statVintage,
          source)) {
        savedCount++;
      }
    }
    return savedCount;
  }

  private long calculateBaseMedian(long grossMedian) {
    return Math.round((grossMedian * BASE_SALARY_RATIO) / ROUNDING_UNIT_TEN_THOUSAND)
        * (long) ROUNDING_UNIT_TEN_THOUSAND;
  }

  private boolean saveOrUpdateObservation(
      String jobFamily,
      String experienceBracket,
      String region,
      WageDefinition wageDefinition,
      long wageMedian,
      String basePeriod,
      String statVintage,
      WageStatSource source) {

    Optional<WageStatObservation> existing =
        repository
            .findByJobFamilyAndExperienceBracketAndRegionAndWageDefinitionAndBasePeriodAndStatVintage(
                jobFamily, experienceBracket, region, wageDefinition, basePeriod, statVintage);

    // 실측(KOSIS)만 기존 행을 덮는다. 폴백 상수가 실측을 덮으면 기준선이 상수로 퇴화한다.
    if (existing.isPresent() && source != WageStatSource.KOSIS) {
      return false;
    }

    WageStatObservation observation =
        WageStatObservation.builder()
            .id(existing.map(WageStatObservation::getId).orElseGet(UUID::randomUUID))
            .jobFamily(jobFamily)
            .experienceBracket(experienceBracket)
            .region(region)
            .wageDefinition(wageDefinition)
            .wageMedian(wageMedian)
            .basePeriod(basePeriod)
            .statVintage(statVintage)
            .source(source)
            .build();
    repository.save(observation);
    return true;
  }
}
