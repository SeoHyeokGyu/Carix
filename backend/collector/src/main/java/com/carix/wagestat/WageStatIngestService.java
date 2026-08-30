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

/** KOSIS 임금 통계를 수집·보간하여 core.wage_stat_observation 에 적재하는 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WageStatIngestService {

  private static final String BASE_PERIOD_DEFAULT = "2025";
  private static final String REGION_ALL = "ALL";

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

    String currentVintage = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    int ingestedCount = 0;

    for (Map.Entry<String, String> entry : AGE_CODE_TO_EXPERIENCE_BRACKET.entrySet()) {
      String ageCode = entry.getKey();
      String experienceBracket = entry.getValue();

      Map<String, Map<String, Long>> yearlyDistribution =
          kosisClient.fetchWageDistributionByPeriod(ageCode, startYear, endYear);

      if (!yearlyDistribution.isEmpty()) {
        for (Map.Entry<String, Map<String, Long>> yearEntry : yearlyDistribution.entrySet()) {
          String basePeriod = yearEntry.getKey();
          Map<String, Long> counts = yearEntry.getValue();
          long grossMedian = interpolator.calculateAnnualMedianWage(counts);

          if (grossMedian <= 0L) {
            grossMedian = FALLBACK_MEDIAN_BY_BRACKET.getOrDefault(experienceBracket, 45_000_000L);
          }
          long baseMedian = Math.round((grossMedian * BASE_SALARY_RATIO) / 10_000.0) * 10_000L;

          String statVintage = basePeriod + "08";

          for (String jobFamily : JOB_FAMILIES) {
            // 1) 연간 세전 총액 중위임금 (ANNUAL_GROSS_MEDIAN)
            saveOrUpdateObservation(
                jobFamily,
                experienceBracket,
                REGION_ALL,
                WageDefinition.ANNUAL_GROSS_MEDIAN,
                grossMedian,
                basePeriod,
                statVintage);
            ingestedCount++;

            // 2) 연간 정액급여(기본급) 중위임금 (ANNUAL_BASE_MEDIAN)
            saveOrUpdateObservation(
                jobFamily,
                experienceBracket,
                REGION_ALL,
                WageDefinition.ANNUAL_BASE_MEDIAN,
                baseMedian,
                basePeriod,
                statVintage);
            ingestedCount++;
          }
        }
      } else {
        // KOSIS API 응답 부재 시 Fallback 기준선 저장
        long grossMedian = FALLBACK_MEDIAN_BY_BRACKET.getOrDefault(experienceBracket, 45_000_000L);
        long baseMedian = Math.round((grossMedian * BASE_SALARY_RATIO) / 10_000.0) * 10_000L;

        log.info(
            "[임금통계수집] KOSIS 응답 부재로 기본 보간값을 사용합니다 (경력: {}, 세전총액: {}원, 정액급여: {}원)",
            experienceBracket,
            grossMedian,
            baseMedian);

        int start =
            (startYear != null && !startYear.isBlank()) ? Integer.parseInt(startYear.trim()) : 2025;
        int end =
            (endYear != null && !endYear.isBlank()) ? Integer.parseInt(endYear.trim()) : start;

        for (int yr = start; yr <= end; yr++) {
          String basePeriod = String.valueOf(yr);
          String statVintage = basePeriod.equals("2025") ? currentVintage : basePeriod + "08";

          for (String jobFamily : JOB_FAMILIES) {
            // 1) 연간 세전 총액 중위임금 (ANNUAL_GROSS_MEDIAN)
            saveOrUpdateObservation(
                jobFamily,
                experienceBracket,
                REGION_ALL,
                WageDefinition.ANNUAL_GROSS_MEDIAN,
                grossMedian,
                basePeriod,
                statVintage);
            ingestedCount++;

            // 2) 연간 정액급여(기본급) 중위임금 (ANNUAL_BASE_MEDIAN)
            saveOrUpdateObservation(
                jobFamily,
                experienceBracket,
                REGION_ALL,
                WageDefinition.ANNUAL_BASE_MEDIAN,
                baseMedian,
                basePeriod,
                statVintage);
            ingestedCount++;
          }
        }
      }
    }

    log.info("[임금통계수집] KOSIS 임금 통계 수집 완료: 총 {}건의 관측치가 저장/갱신되었습니다.", ingestedCount);
    return ingestedCount;
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
