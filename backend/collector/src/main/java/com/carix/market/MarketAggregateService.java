package com.carix.market;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.JobPostingSkill;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월별 채용 시장 다차원 사전 집계(Pre-aggregation) 배치 엔진.
 *
 * <p>원천 간 중복제거된 공고군({@code dedup_group_id}) 및 원천별 공고군을 기준으로 직군, 경력구간, 지역, 기업규모, 스킬별 수요율과 분위수 연봉을
 * 계산하여 {@code core.market_aggregate} 에 저장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketAggregateService {

  /** 연봉 공개 공고가 하나도 없을 때의 결측률. DDL 기본값과 같은 퍼센트 단위다. */
  private static final BigDecimal HUNDRED_PERCENT = new BigDecimal("100.00");

  private final JobPostingRepository jobPostingRepository;
  private final MarketAggregateRepository marketAggregateRepository;

  /**
   * 최신 수집 데이터 기준 전체 시장 통계를 사전 집계하여 DB에 적재합니다.
   *
   * @return 생성된 집계 레코드 수
   */
  @Transactional
  public int aggregateMarketData() {
    return aggregateAllMarketData();
  }

  /**
   * {@link com.carix.pipeline.dto.MarketAggregateRequest} 옵션에 따라 단일월/단일연도/연도구간/전체 집계를 유연하게 실행합니다.
   *
   * @param request 집계 요청 옵션
   * @return 생성된 집계 레코드 수
   */
  @Transactional
  public int aggregateMarketData(com.carix.pipeline.dto.MarketAggregateRequest request) {
    if (request == null) {
      return aggregateAllMarketData();
    }

    if (request.month() != null && !request.month().isBlank()) {
      return aggregateMarketDataForMonth(request.month());
    }

    if (request.year() != null && !request.year().isBlank()) {
      return aggregateMarketDataForYear(request.year());
    }

    if (request.startYear() != null && !request.startYear().isBlank()) {
      return aggregateMarketDataForYearRange(request.startYear(), request.endYear());
    }

    if (Boolean.TRUE.equals(request.all())) {
      return aggregateAllMarketData();
    }

    return aggregateAllMarketData();
  }

  /**
   * DB에 적재된 채용공고의 모든 활동 기준월(firstSeenMonth, lastSeenMonth, postedDate 등)을 자동 추출하여 전체 일괄 집계합니다.
   *
   * @return 생성된 총 집계 레코드 수
   */
  @Transactional
  public int aggregateAllMarketData() {
    List<JobPosting> allPostings = jobPostingRepository.findAll();
    if (allPostings.isEmpty()) {
      log.warn("[시장사전집계] 적재된 채용공고가 없어 집계를 건너뜁니다.");
      return 0;
    }

    Set<String> distinctMonths = extractActiveMonths(allPostings);
    log.info(
        "[시장사전집계] 채용공고에서 발견된 {}개 기준월에 대해 전체 일괄 집계를 시작합니다: {}",
        distinctMonths.size(),
        distinctMonths);
    return aggregateMarketDataForMonths(new ArrayList<>(distinctMonths), allPostings);
  }

  /**
   * 특정 연도(1월~12월) 전체에 대해 시장 통계를 사전 집계합니다.
   *
   * @param year 대상 연도 (예: "2025")
   * @return 생성된 총 집계 레코드 수
   */
  @Transactional
  public int aggregateMarketDataForYear(String year) {
    return aggregateMarketDataForYearRange(year, year);
  }

  /**
   * 지정한 연도 구간(startYear ~ endYear)의 모든 월에 대해 시장 통계를 사전 집계합니다.
   *
   * @param startYear 시작 연도 (예: "2020")
   * @param endYear 종료 연도 (예: "2025")
   * @return 생성된 총 집계 레코드 수
   */
  @Transactional
  public int aggregateMarketDataForYearRange(String startYear, String endYear) {
    int start = Integer.parseInt(startYear.trim());
    int end = (endYear != null && !endYear.isBlank()) ? Integer.parseInt(endYear.trim()) : start;

    List<String> months = new ArrayList<>();
    for (int y = start; y <= end; y++) {
      for (int m = 1; m <= 12; m++) {
        months.add(String.format("%04d%02d", y, m));
      }
    }

    log.info("[시장사전집계] 연도 구간({}~{}) 총 {}개 월에 대한 사전 집계를 시작합니다.", start, end, months.size());
    return aggregateMarketDataForMonths(months);
  }

  /**
   * 지정된 여러 기준년월 목록에 대해 순차적으로 시장 통계를 사전 집계합니다.
   *
   * @param months 기준년월 목록 (예: ["202501", "202502", ...])
   * @return 생성된 총 집계 레코드 수
   */
  @Transactional
  public int aggregateMarketDataForMonths(List<String> months) {
    List<JobPosting> allPostings = jobPostingRepository.findAll();
    return aggregateMarketDataForMonths(months, allPostings);
  }

  private int aggregateMarketDataForMonths(List<String> months, List<JobPosting> allPostings) {
    if (allPostings.isEmpty() || months.isEmpty()) {
      return 0;
    }

    int totalAggregates = 0;
    for (String m : months) {
      totalAggregates += executeAggregationForMonth(normalizeMonth(m), allPostings);
    }
    return totalAggregates;
  }

  /**
   * 특정 기준년월에 대해 시장 통계를 사전 집계하여 DB에 적재합니다.
   *
   * @param baseYearMonth 기준년월 (형식: "yyyyMM" 또는 "yyyy-MM", 예: "202608" 또는 "2026-08")
   * @return 생성된 집계 레코드 수
   */
  @Transactional
  public int aggregateMarketDataForMonth(String baseYearMonth) {
    String normalizedMonth = normalizeMonth(baseYearMonth);
    List<JobPosting> allPostings = jobPostingRepository.findAll();

    if (allPostings.isEmpty()) {
      log.warn("[시장사전집계] 적재된 채용공고가 없어 집계를 건너뜁니다.");
      return 0;
    }

    return executeAggregationForMonth(normalizedMonth, allPostings);
  }

  private int executeAggregationForMonth(String baseYearMonth, List<JobPosting> allPostings) {
    log.info("[시장사전집계] 기준년월({})에 대한 다차원 사전 집계를 시작합니다...", baseYearMonth);

    // 1. 기존 해당 월 집계 데이터 삭제 후 재생성 (Idempotent Upsert)
    marketAggregateRepository.deleteByBaseYearMonth(baseYearMonth);

    // 2. 활동월(firstSeenMonth ~ lastSeenMonth) 기준 월별 귀속 필터링 (b18)
    List<JobPosting> activePostings = filterActivePostingsForMonth(allPostings, baseYearMonth);
    if (activePostings.isEmpty()) {
      log.info("[시장사전집계] 기준년월 {}에 해당하는 활동 공고가 없어 0건 적재 완료.", baseYearMonth);
      return 0;
    }

    List<MarketAggregate> aggregates = new ArrayList<>();

    // 3. 통합 관점 ("ALL" source, dedup_group_id 기준) 집계
    aggregates.addAll(buildAggregatesForSource("ALL", activePostings, baseYearMonth, true));

    // 4. 개별 원천별 관점 ("JOBKOREA", "SARAMIN") 집계
    Map<String, List<JobPosting>> bySource =
        activePostings.stream()
            .collect(Collectors.groupingBy(p -> p.getSource() != null ? p.getSource() : "UNKNOWN"));
    for (Map.Entry<String, List<JobPosting>> entry : bySource.entrySet()) {
      aggregates.addAll(
          buildAggregatesForSource(entry.getKey(), entry.getValue(), baseYearMonth, false));
    }

    marketAggregateRepository.saveAll(aggregates);
    log.info("[시장사전집계] 기준년월 {} 집계 완료 ({}건 적재).", baseYearMonth, aggregates.size());

    return aggregates.size();
  }

  /**
   * 지정된 기준년월(baseYearMonth)에 활동 중인(게시 중인) 공고만 선별합니다.
   *
   * <p>귀속 기준: {@code firstSeenMonth <= baseYearMonth <= lastSeenMonth}
   */
  public List<JobPosting> filterActivePostingsForMonth(
      List<JobPosting> postings, String baseYearMonth) {
    if (postings == null || postings.isEmpty() || baseYearMonth == null) {
      return Collections.emptyList();
    }
    String targetMonth = normalizeMonth(baseYearMonth);
    return postings.stream()
        .filter(p -> isPostingActiveInMonth(p, targetMonth))
        .collect(Collectors.toList());
  }

  /** 특정 공고가 대상 월(targetMonth, YYYYMM)에 활동 중인지 판정합니다. */
  public boolean isPostingActiveInMonth(JobPosting p, String targetMonth) {
    if (p == null || targetMonth == null) {
      return false;
    }
    String startMonth = resolveStartMonth(p);
    if (startMonth == null) {
      return false;
    }
    String endMonth = resolveEndMonth(p, startMonth);
    if (startMonth.compareTo(endMonth) > 0) {
      endMonth = startMonth;
    }
    return startMonth.compareTo(targetMonth) <= 0 && targetMonth.compareTo(endMonth) <= 0;
  }

  private String resolveStartMonth(JobPosting p) {
    if (p.getFirstSeenMonth() != null) {
      String cleaned = p.getFirstSeenMonth().replace("-", "").trim();
      if (cleaned.matches("\\d{6}")) {
        return cleaned;
      }
    }
    if (p.getPostedStartDate() != null) {
      return p.getPostedStartDate().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }
    if (p.getCollectedAt() != null) {
      return YearMonth.from(p.getCollectedAt().atZone(ZoneId.of("Asia/Seoul")))
          .format(DateTimeFormatter.ofPattern("yyyyMM"));
    }
    return null;
  }

  private String resolveEndMonth(JobPosting p, String startMonth) {
    if (p.getLastSeenMonth() != null) {
      String cleaned = p.getLastSeenMonth().replace("-", "").trim();
      if (cleaned.matches("\\d{6}")) {
        return cleaned;
      }
    }
    if (p.getPostedEndDate() != null) {
      return p.getPostedEndDate().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }
    return startMonth;
  }

  private String normalizeMonth(String month) {
    if (month == null) {
      throw new IllegalArgumentException("기준년월은 필수입니다.");
    }
    String cleaned = month.replace("-", "").trim();
    if (!cleaned.matches("\\d{6}")) {
      throw new IllegalArgumentException(
          "기준년월 형식이 올바르지 않습니다 (입력값: " + month + ", 필요형식: YYYYMM 또는 YYYY-MM)");
    }
    return cleaned;
  }

  private Set<String> extractActiveMonths(List<JobPosting> postings) {
    Set<String> months = new java.util.TreeSet<>();
    DateTimeFormatter yyyyMM = DateTimeFormatter.ofPattern("yyyyMM");

    for (JobPosting p : postings) {
      String start = resolveStartMonth(p);
      if (start != null) {
        String end = resolveEndMonth(p, start);
        try {
          YearMonth startYm = YearMonth.parse(start, yyyyMM);
          YearMonth endYm = YearMonth.parse(end, yyyyMM);
          if (startYm.isAfter(endYm)) {
            endYm = startYm;
          }
          if (ChronoUnit.MONTHS.between(startYm, endYm) <= 36) {
            YearMonth cur = startYm;
            while (!cur.isAfter(endYm)) {
              months.add(cur.format(yyyyMM));
              cur = cur.plusMonths(1);
            }
          } else {
            months.add(start);
            months.add(end);
          }
        } catch (Exception e) {
          months.add(start);
        }
      }
    }

    if (months.isEmpty()) {
      months.add(LocalDate.now().format(yyyyMM));
    }
    return months;
  }

  private List<MarketAggregate> buildAggregatesForSource(
      String source, List<JobPosting> postings, String baseYearMonth, boolean useDedupGroup) {

    List<MarketAggregate> results = new ArrayList<>();

    // 중복 제거 적용 시 dedup_group_id 당 대표 공고 1건만 선별
    List<JobPosting> targetPostings;
    if (useDedupGroup) {
      Map<UUID, JobPosting> deduped = new HashMap<>();
      for (JobPosting p : postings) {
        UUID key = p.getDedupGroupId() != null ? p.getDedupGroupId() : p.getId();
        deduped.putIfAbsent(key, p);
      }
      targetPostings = new ArrayList<>(deduped.values());
    } else {
      targetPostings = postings;
    }

    // 직군 목록 ("ALL" 포함)
    Set<String> jobFamilies = new HashSet<>();
    jobFamilies.add("ALL");
    targetPostings.forEach(
        p -> {
          if (p.getJobFamily() != null) jobFamilies.add(p.getJobFamily());
        });

    // 경력 구간 목록
    List<String> brackets = List.of("ALL", "ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD");

    for (String jf : jobFamilies) {
      for (String bracket : brackets) {
        List<JobPosting> slice = filterSlice(targetPostings, jf, bracket);
        if (slice.isEmpty()) {
          continue;
        }

        int totalPostingsInSlice = slice.size();

        // 1) 직군/경력 슬라이스의 전체 메트릭 (skill_id = null)
        int sliceDisclosed = countDisclosedPostings(slice);
        BigDecimal sliceMissingRate =
            calculateSalaryMissingRate(sliceDisclosed, totalPostingsInSlice);
        results.add(
            MarketAggregate.builder()
                .source(source)
                .baseYearMonth(baseYearMonth)
                .jobFamily(jf)
                .region("ALL")
                .experienceBracket(bracket)
                .employmentType("ALL")
                .companySize("ALL")
                .skillId(null)
                .postingCount(totalPostingsInSlice)
                .totalAggregatePostings(totalPostingsInSlice)
                .demandRate(BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP))
                .salaryMissingRatePercent(sliceMissingRate)
                .build());

        // 2) 슬라이스 내 개별 스킬별 수요 지수 계산
        Map<UUID, Integer> skillPostingCounts = new HashMap<>();
        Map<UUID, Integer> skillDisclosedCounts = new HashMap<>();

        for (JobPosting p : slice) {
          Set<UUID> uniqueSkillsInPosting = new HashSet<>();
          if (p.getJobPostingSkills() != null) {
            for (JobPostingSkill jps : p.getJobPostingSkills()) {
              if (jps.getSkill() != null) {
                uniqueSkillsInPosting.add(jps.getSkill().getId());
              }
            }
          }

          boolean hasSalary = p.getSalaryMin() != null && p.getSalaryMax() != null;
          for (UUID skillId : uniqueSkillsInPosting) {
            skillPostingCounts.put(skillId, skillPostingCounts.getOrDefault(skillId, 0) + 1);
            if (hasSalary) {
              skillDisclosedCounts.put(skillId, skillDisclosedCounts.getOrDefault(skillId, 0) + 1);
            }
          }
        }

        for (Map.Entry<UUID, Integer> entry : skillPostingCounts.entrySet()) {
          UUID skillId = entry.getKey();
          int skillCount = entry.getValue();
          BigDecimal demandRate =
              BigDecimal.valueOf((double) skillCount / totalPostingsInSlice)
                  .setScale(4, RoundingMode.HALF_UP);

          int disclosedCount = skillDisclosedCounts.getOrDefault(skillId, 0);
          BigDecimal skillMissingRate = calculateSalaryMissingRate(disclosedCount, skillCount);

          results.add(
              MarketAggregate.builder()
                  .source(source)
                  .baseYearMonth(baseYearMonth)
                  .jobFamily(jf)
                  .region("ALL")
                  .experienceBracket(bracket)
                  .employmentType("ALL")
                  .companySize("ALL")
                  .skillId(skillId)
                  .postingCount(skillCount)
                  .totalAggregatePostings(totalPostingsInSlice)
                  .demandRate(demandRate)
                  .salaryMissingRatePercent(skillMissingRate)
                  .build());
        }
      }
    }

    return results;
  }

  private List<JobPosting> filterSlice(
      List<JobPosting> postings, String jobFamily, String bracket) {
    return postings.stream()
        .filter(p -> "ALL".equals(jobFamily) || jobFamily.equalsIgnoreCase(p.getJobFamily()))
        .filter(p -> "ALL".equals(bracket) || matchesBracket(p.getExperienceMin(), bracket))
        .collect(Collectors.toList());
  }

  private boolean matchesBracket(Integer expMin, String bracket) {
    int min = expMin != null ? expMin : 0;
    return switch (bracket) {
      case "ENTRY" -> min == 0;
      case "JUNIOR" -> min >= 1 && min <= 3;
      case "MID" -> min >= 4 && min <= 7;
      case "SENIOR" -> min >= 8 && min <= 12;
      case "LEAD" -> min >= 13;
      default -> true;
    };
  }

  private int countDisclosedPostings(List<JobPosting> postings) {
    int count = 0;
    for (JobPosting p : postings) {
      if (p.getSalaryMin() != null && p.getSalaryMax() != null) {
        count++;
      }
    }
    return count;
  }

  private BigDecimal calculateSalaryMissingRate(int disclosedCount, int totalPostings) {
    if (totalPostings == 0 || disclosedCount == 0) {
      return HUNDRED_PERCENT;
    }
    double missingRatio = (double) (totalPostings - disclosedCount) / totalPostings;
    return BigDecimal.valueOf(missingRatio * 100).setScale(2, RoundingMode.HALF_UP);
  }
}
