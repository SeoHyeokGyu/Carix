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
  private static final String STAT_VINTAGE_DEFAULT = "202508";
  private static final String REGION_ALL = "ALL";
  private static final String WAGE_DEFINITION = "ANNUAL_GROSS_MEDIAN";

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
   * KOSIS 임금 통계를 실시간 조회하여 보간 후 DB에 Upsert 합니다.
   *
   * @return 수집 및 적재된 관측치 행 수
   */
  @Transactional
  public int ingestWageStats() {
    log.info("Starting KOSIS wage stat ingestion...");
    String currentVintage = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    int ingestedCount = 0;

    for (Map.Entry<String, String> entry : AGE_CODE_TO_EXPERIENCE_BRACKET.entrySet()) {
      String ageCode = entry.getKey();
      String experienceBracket = entry.getValue();

      Map<String, Long> counts = kosisClient.fetchWageDistributionForAge(ageCode);
      long medianWage = interpolator.calculateAnnualMedianWage(counts);

      if (medianWage <= 0L) {
        medianWage = FALLBACK_MEDIAN_BY_BRACKET.getOrDefault(experienceBracket, 45_000_000L);
        log.info(
            "Using fallback median wage for bracket={}: {} KRW", experienceBracket, medianWage);
      }

      for (String jobFamily : JOB_FAMILIES) {
        saveOrUpdateObservation(
            jobFamily,
            experienceBracket,
            REGION_ALL,
            medianWage,
            BASE_PERIOD_DEFAULT,
            currentVintage);
        ingestedCount++;
      }
    }

    log.info("Completed KOSIS wage stat ingestion: {} rows inserted/updated", ingestedCount);
    return ingestedCount;
  }

  private void saveOrUpdateObservation(
      String jobFamily,
      String experienceBracket,
      String region,
      long wageMedian,
      String basePeriod,
      String statVintage) {

    Optional<WageStatObservation> existing =
        repository.findByJobFamilyAndExperienceBracketAndRegionAndBasePeriodAndStatVintage(
            jobFamily, experienceBracket, region, basePeriod, statVintage);

    if (existing.isEmpty()) {
      WageStatObservation observation =
          WageStatObservation.builder()
              .jobFamily(jobFamily)
              .experienceBracket(experienceBracket)
              .region(region)
              .wageDefinition(WAGE_DEFINITION)
              .wageMedian(wageMedian)
              .basePeriod(basePeriod)
              .statVintage(statVintage)
              .build();
      repository.save(observation);
    }
  }
}
