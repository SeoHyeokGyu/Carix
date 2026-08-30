package com.carix.wagestat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private static final int DEFAULT_BASE_YEAR = 2025;
  private static final String REGION_ALL = "ALL";

  /** 통계청 고용형태별근로실태조사 정기 공시 월 (매년 8월 기준 빈티지). */
  private static final String STAT_VINTAGE_MONTH_SUFFIX = "08";

  /** 만원 단위 반올림 단위. */
  private static final double ROUNDING_UNIT_TEN_THOUSAND = 10_000.0;

  /** 브래킷별 Fallback 누락 시 적용되는 기본 연간 중위연봉 (4,500만 원). */
  private static final long DEFAULT_MEDIAN_FALLBACK = 45_000_000L;

  /** 실시간 빈티지 생성용 포맷터. */
  private static final DateTimeFormatter VINTAGE_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMM");

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
    log.info(
        "[임금통계수집] KOSIS 임금 통계 수집 및 보간 작업을 시작합니다... (기간: {} ~ {})",
        startYear != null ? startYear : "최신",
        endYear != null ? endYear : (startYear != null ? startYear : "최신"));

    int ingestedCount = 0;
    for (Map.Entry<String, String> entry : AGE_CODE_TO_EXPERIENCE_BRACKET.entrySet()) {
      ingestedCount += ingestBracket(entry.getKey(), entry.getValue(), startYear, endYear);
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
      if (grossMedian <= 0L) {
        grossMedian =
            FALLBACK_MEDIAN_BY_BRACKET.getOrDefault(experienceBracket, DEFAULT_MEDIAN_FALLBACK);
      }
      String statVintage = basePeriod + STAT_VINTAGE_MONTH_SUFFIX;
      count += saveDualWageObservations(experienceBracket, grossMedian, basePeriod, statVintage);
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

    String currentVintage = LocalDate.now().format(VINTAGE_DATE_FORMATTER);
    int start =
        (startYear != null && !startYear.isBlank())
            ? Integer.parseInt(startYear.trim())
            : DEFAULT_BASE_YEAR;
    int end = (endYear != null && !endYear.isBlank()) ? Integer.parseInt(endYear.trim()) : start;

    int count = 0;
    for (int yr = start; yr <= end; yr++) {
      String basePeriod = String.valueOf(yr);
      String statVintage =
          basePeriod.equals(BASE_PERIOD_DEFAULT)
              ? currentVintage
              : basePeriod + STAT_VINTAGE_MONTH_SUFFIX;
      count += saveDualWageObservations(experienceBracket, grossMedian, basePeriod, statVintage);
    }
    return count;
  }

  /** 지정된 경력구간/연도에 대해 모든 직군(JOB_FAMILIES)의 세전총액(GROSS)과 정액급여(BASE) 관측치를 저장합니다. */
  private int saveDualWageObservations(
      String experienceBracket, long grossMedian, String basePeriod, String statVintage) {
    long baseMedian = calculateBaseMedian(grossMedian);
    int savedCount = 0;

    for (String jobFamily : JOB_FAMILIES) {
      saveOrUpdateObservation(
          jobFamily,
          experienceBracket,
          REGION_ALL,
          WageDefinition.ANNUAL_GROSS_MEDIAN,
          grossMedian,
          basePeriod,
          statVintage);
      savedCount++;

      saveOrUpdateObservation(
          jobFamily,
          experienceBracket,
          REGION_ALL,
          WageDefinition.ANNUAL_BASE_MEDIAN,
          baseMedian,
          basePeriod,
          statVintage);
      savedCount++;
    }
    return savedCount;
  }

  private long calculateBaseMedian(long grossMedian) {
    return Math.round((grossMedian * BASE_SALARY_RATIO) / ROUNDING_UNIT_TEN_THOUSAND)
        * (long) ROUNDING_UNIT_TEN_THOUSAND;
  }

  private void saveOrUpdateObservation(
      String jobFamily,
      String experienceBracket,
      String region,
      WageDefinition wageDefinition,
      long wageMedian,
      String basePeriod,
      String statVintage) {

    Optional<WageStatObservation> existing =
        repository
            .findByJobFamilyAndExperienceBracketAndRegionAndWageDefinitionAndBasePeriodAndStatVintage(
                jobFamily, experienceBracket, region, wageDefinition, basePeriod, statVintage);

    if (existing.isEmpty()) {
      WageStatObservation observation =
          WageStatObservation.builder()
              .jobFamily(jobFamily)
              .experienceBracket(experienceBracket)
              .region(region)
              .wageDefinition(wageDefinition)
              .wageMedian(wageMedian)
              .basePeriod(basePeriod)
              .statVintage(statVintage)
              .build();
      repository.save(observation);
    }
  }
}
