package com.carix.pipeline.service;

import com.carix.market.MarketAggregate;
import com.carix.market.MarketAggregateRepository;
import com.carix.pipeline.dto.CollectionPriorityItem;
import com.carix.pipeline.dto.CollectionPriorityItem.PriorityGrade;
import com.carix.pipeline.dto.CollectionPriorityResponse;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MVI 시장 데이터 수집 우선순위 산출 서비스 (개발 계획 §2 b8b).
 *
 * <p>동료집단 사전 집계 데이터({@code core.market_aggregate})의 직군, 지역, 경력 브래킷별 표본수를 분석하여, MVI 산출 시 동료집단
 * 완화(Level 1~5) 또는 표본 부족(N &lt; 10)을 유발하는 결손 차원을 탐지하고 크롤러의 수집 타깃 우선순위를 도출합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MviCollectionPriorityService {

  public static final int DEFAULT_MIN_SAMPLE_SIZE = 10;
  private static final String DEFAULT_SOURCE = "ALL";

  private static final List<String> STANDARD_JOB_FAMILIES =
      List.of("DEVELOPER", "DATA", "AI", "DEVOPS", "DESIGN", "PRODUCT");

  private static final List<String> STANDARD_BRACKETS =
      List.of("ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD");

  private final MarketAggregateRepository marketAggregateRepository;

  /**
   * 기준월 및 원천 기준 시장 데이터 수집 우선순위를 산출합니다.
   *
   * @param baseYearMonth 기준 년월 (YYYYMM, null 시 ALL 행이 있는 당월 이하 최신 월, 없으면 당월)
   * @param source 원천 플랫폼 (ALL, JOBKOREA, SARAMIN, null 시 ALL)
   * @param minSampleSize 최소 기준 표본수 (null 또는 &lt;= 0 시 기본 10)
   * @param underSampledOnly 표본 부족(N &lt; minSampleSize) 항목만 필터링할지 여부
   * @return 수집 우선순위 산출 응답
   */
  @Transactional(readOnly = true)
  public CollectionPriorityResponse calculatePriorities(
      String baseYearMonth, String source, Integer minSampleSize, Boolean underSampledOnly) {

    int effectiveMinSample =
        (minSampleSize != null && minSampleSize > 0) ? minSampleSize : DEFAULT_MIN_SAMPLE_SIZE;
    String effectiveSource =
        (source != null && !source.isBlank()) ? source.trim().toUpperCase() : DEFAULT_SOURCE;
    String effectiveMonth = resolveTargetMonth(baseYearMonth);

    // NOTE: 기본 ALL 도 source='ALL' 행만 읽는다. 원천별 행을 합산하면 원천 합산 행과 이중 계산된다.
    List<MarketAggregate> aggregates =
        marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource(
            effectiveMonth, effectiveSource);

    // (jobFamily, region, experienceBracket) -> sum(postingCount)
    Map<DimensionKey, Integer> countMap = new HashMap<>();
    Set<String> observedRegions = new LinkedHashSet<>();
    observedRegions.add("ALL");

    Set<String> observedJobFamilies = new LinkedHashSet<>(STANDARD_JOB_FAMILIES);
    Set<String> observedBrackets = new LinkedHashSet<>(STANDARD_BRACKETS);

    for (MarketAggregate agg : aggregates) {
      if (agg.getJobFamily() != null) observedJobFamilies.add(agg.getJobFamily());
      if (agg.getRegion() != null) observedRegions.add(agg.getRegion());
      if (agg.getExperienceBracket() != null) observedBrackets.add(agg.getExperienceBracket());

      DimensionKey key =
          new DimensionKey(
              agg.getJobFamily() != null ? agg.getJobFamily() : "ALL",
              agg.getRegion() != null ? agg.getRegion() : "ALL",
              agg.getExperienceBracket() != null ? agg.getExperienceBracket() : "ALL");

      countMap.put(
          key,
          countMap.getOrDefault(key, 0)
              + (agg.getPostingCount() != null ? agg.getPostingCount() : 0));
    }

    // 평가할 전체 차원 조합 생성
    Set<DimensionKey> allKeys = new HashSet<>(countMap.keySet());
    for (String jf : observedJobFamilies) {
      for (String br : observedBrackets) {
        for (String reg : observedRegions) {
          allKeys.add(new DimensionKey(jf, reg, br));
        }
      }
    }

    List<CollectionPriorityItem> items = new ArrayList<>();
    int underSampledCount = 0;

    for (DimensionKey key : allKeys) {
      // "ALL" 단일 집계 차원보다는 구체적인 차원 우선 분석
      int count = countMap.getOrDefault(key, 0);
      int deficit = Math.max(0, effectiveMinSample - count);
      PriorityGrade grade = determineGrade(count, effectiveMinSample);

      if (grade != PriorityGrade.SUFFICIENT) {
        underSampledCount++;
      } else if (Boolean.TRUE.equals(underSampledOnly)) {
        continue;
      }

      String reason = buildPriorityReason(count, effectiveMinSample, deficit, grade);
      String hint = buildSearchHint(key.jobFamily(), key.experienceBracket(), key.region());

      items.add(
          new CollectionPriorityItem(
              key.jobFamily(),
              key.region(),
              key.experienceBracket(),
              count,
              effectiveMinSample,
              deficit,
              grade,
              reason,
              hint));
    }

    // 우선순위 정렬: 등급(CRITICAL -> HIGH -> MEDIUM -> SUFFICIENT) -> 결손수 DESC -> 표본수 ASC -> 직군 ASC
    items.sort(
        Comparator.comparing(CollectionPriorityItem::priorityGrade)
            .thenComparing(
                Comparator.comparingInt(CollectionPriorityItem::sampleDeficit).reversed())
            .thenComparingInt(CollectionPriorityItem::currentSampleCount)
            .thenComparing(CollectionPriorityItem::jobFamily)
            .thenComparing(CollectionPriorityItem::experienceBracket)
            .thenComparing(CollectionPriorityItem::region));

    return new CollectionPriorityResponse(
        effectiveMonth,
        effectiveSource,
        effectiveMinSample,
        allKeys.size(),
        underSampledCount,
        items);
  }

  private String resolveTargetMonth(String baseYearMonth) {
    if (baseYearMonth != null && !baseYearMonth.isBlank()) {
      return baseYearMonth.replace("-", "").trim();
    }
    // 마감일로 늘어난 달에는 SARAMIN·JUMPIT 프레임 행만 있고 ALL 행이 없어, ALL 행이 있는 당월 이하 최신 월을 잡는다.
    String current = YearMonth.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMM"));
    return marketAggregateRepository
        .findLatestYearMonthForSource(DEFAULT_SOURCE, current)
        .orElse(current);
  }

  private PriorityGrade determineGrade(int count, int minSample) {
    if (count == 0) {
      return PriorityGrade.CRITICAL;
    }
    if (count < (minSample + 1) / 2) {
      return PriorityGrade.HIGH;
    }
    if (count < minSample) {
      return PriorityGrade.MEDIUM;
    }
    return PriorityGrade.SUFFICIENT;
  }

  private String buildPriorityReason(int count, int minSample, int deficit, PriorityGrade grade) {
    return switch (grade) {
      case CRITICAL -> "해당 조건의 공고 표본이 0건으로 완전 결측 상태입니다. (MVI 산출 불가, 최우선 긴급 수집 대상)";
      case HIGH ->
          String.format(
              "현재 표본 %d건으로 기준(%d건) 대비 극심한 결손(-%d건) 상태입니다. (Level 4~5 완화 폴백 빈발)",
              count, minSample, deficit);
      case MEDIUM ->
          String.format(
              "현재 표본 %d건으로 최소 기준(%d건)에 미달(-%d건)합니다. (Level 2~3 완화 필요)", count, minSample, deficit);
      case SUFFICIENT ->
          String.format("현재 표본 %d건으로 최소 기준(%d건)을 충족합니다. (안정 표본 확보)", count, minSample);
    };
  }

  private String buildSearchHint(String jobFamily, String bracket, String region) {
    String regionPart = "ALL".equalsIgnoreCase(region) ? "" : region;
    String bracketPart = "ALL".equalsIgnoreCase(bracket) ? "" : bracket;
    String jfPart = "ALL".equalsIgnoreCase(jobFamily) ? "" : jobFamily;

    return String.format("%s %s %s", jfPart, bracketPart, regionPart)
        .replaceAll("\\s+", " ")
        .trim();
  }

  private record DimensionKey(String jobFamily, String region, String experienceBracket) {}
}
