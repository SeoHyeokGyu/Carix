package com.carix.market;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.JobPostingSkill;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

  private final JobPostingRepository jobPostingRepository;
  private final MarketAggregateRepository marketAggregateRepository;

  /**
   * 최신 수집 데이터 기준 전체 시장 통계를 사전 집계하여 DB에 적재합니다.
   *
   * @return 생성된 집계 레코드 수
   */
  @Transactional
  public int aggregateMarketData() {
    String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    return aggregateMarketDataForMonth(currentMonth);
  }

  /**
   * 특정 기준년월에 대해 시장 통계를 사전 집계하여 DB에 적재합니다.
   *
   * @param baseYearMonth 기준년월 (형식: "yyyy-MM", 예: "2026-08")
   * @return 생성된 집계 레코드 수
   */
  @Transactional
  public int aggregateMarketDataForMonth(String baseYearMonth) {
    log.info("[MarketAggregate] Starting pre-aggregation for cohort month: {}", baseYearMonth);
    List<JobPosting> allPostings = jobPostingRepository.findAll();

    if (allPostings.isEmpty()) {
      log.warn("[MarketAggregate] No job postings found. Skipping aggregation.");
      return 0;
    }

    // 1. 기존 해당 월 집계 데이터 삭제 후 재생성 (Idempotent Upsert)
    marketAggregateRepository.deleteByBaseYearMonth(baseYearMonth);

    List<MarketAggregate> aggregates = new ArrayList<>();

    // 2. 통합 관점 ("ALL" source, dedup_group_id 기준) 집계
    aggregates.addAll(buildAggregatesForSource("ALL", allPostings, baseYearMonth, true));

    // 3. 개별 원천별 관점 ("JOBKOREA", "SARAMIN") 집계
    Map<String, List<JobPosting>> bySource =
        allPostings.stream().collect(Collectors.groupingBy(JobPosting::getSource));
    for (Map.Entry<String, List<JobPosting>> entry : bySource.entrySet()) {
      aggregates.addAll(
          buildAggregatesForSource(entry.getKey(), entry.getValue(), baseYearMonth, false));
    }

    marketAggregateRepository.saveAll(aggregates);
    log.info(
        "[MarketAggregate] Pre-aggregation completed. Total {} aggregate rows saved for month {}",
        aggregates.size(),
        baseYearMonth);

    return aggregates.size();
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

        // 1) 직군/경력 슬라이스의 연봉 통계 및 전체 메트릭 (skill_id = null)
        SalaryStats salaryStats = calculateSalaryStats(slice);
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
                .salaryMin(salaryStats.min())
                .salaryP25(salaryStats.p25())
                .salaryMedian(salaryStats.median())
                .salaryP75(salaryStats.p75())
                .salaryMax(salaryStats.max())
                .salaryMissingRate(salaryStats.missingRate())
                .build());

        // 2) 슬라이스 내 개별 스킬별 수요 지수 계산
        Map<UUID, Integer> skillPostingCounts = new HashMap<>();
        Map<UUID, List<Long>> skillSalaries = new HashMap<>();

        for (JobPosting p : slice) {
          Set<UUID> uniqueSkillsInPosting = new HashSet<>();
          if (p.getJobPostingSkills() != null) {
            for (JobPostingSkill jps : p.getJobPostingSkills()) {
              if (jps.getSkill() != null) {
                uniqueSkillsInPosting.add(jps.getSkill().getId());
              }
            }
          }

          for (UUID skillId : uniqueSkillsInPosting) {
            skillPostingCounts.put(skillId, skillPostingCounts.getOrDefault(skillId, 0) + 1);
            if (p.getSalaryMin() != null && p.getSalaryMax() != null) {
              long avgSalary = (p.getSalaryMin() + p.getSalaryMax()) / 2;
              skillSalaries.computeIfAbsent(skillId, k -> new ArrayList<>()).add(avgSalary);
            }
          }
        }

        for (Map.Entry<UUID, Integer> entry : skillPostingCounts.entrySet()) {
          UUID skillId = entry.getKey();
          int skillCount = entry.getValue();
          BigDecimal demandRate =
              BigDecimal.valueOf((double) skillCount / totalPostingsInSlice)
                  .setScale(4, RoundingMode.HALF_UP);

          SalaryStats skillSalaryStats =
              calculateSalaryStatsFromValues(skillSalaries.get(skillId), skillCount);

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
                  .salaryMin(skillSalaryStats.min())
                  .salaryP25(skillSalaryStats.p25())
                  .salaryMedian(skillSalaryStats.median())
                  .salaryP75(skillSalaryStats.p75())
                  .salaryMax(skillSalaryStats.max())
                  .salaryMissingRate(skillSalaryStats.missingRate())
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

  private SalaryStats calculateSalaryStats(List<JobPosting> postings) {
    List<Long> values = new ArrayList<>();
    for (JobPosting p : postings) {
      if (p.getSalaryMin() != null && p.getSalaryMax() != null) {
        values.add((p.getSalaryMin() + p.getSalaryMax()) / 2);
      }
    }
    return calculateSalaryStatsFromValues(values, postings.size());
  }

  private SalaryStats calculateSalaryStatsFromValues(List<Long> values, int totalPostings) {
    if (values == null || values.isEmpty() || totalPostings == 0) {
      return new SalaryStats(
          null, null, null, null, null, BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP));
    }

    Collections.sort(values);
    int n = values.size();
    Long min = values.get(0);
    Long p25 = values.get((int) Math.floor(n * 0.25));
    Long median = values.get((int) Math.floor(n * 0.50));
    Long p75 = values.get(Math.min(n - 1, (int) Math.floor(n * 0.75)));
    Long max = values.get(n - 1);

    double missingRatio = (double) (totalPostings - n) / totalPostings;
    BigDecimal missingRate = BigDecimal.valueOf(missingRatio).setScale(4, RoundingMode.HALF_UP);

    return new SalaryStats(min, p25, median, p75, max, missingRate);
  }

  private record SalaryStats(
      Long min, Long p25, Long median, Long p75, Long max, BigDecimal missingRate) {}
}
