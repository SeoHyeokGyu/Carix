package com.carix.pipeline.dto.explore;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 수집 데이터 — 공고 목록. 설계 §5.2. {@code month} 는 실제로 조회한 월(공고가 하나도 없으면 null), {@code current} 는 그 월이
 * 이번 달(UTC)인지.
 */
public record PostingPage(
    String month, boolean current, long total, int page, int size, List<Item> items) {

  public record Item(
      String source,
      String externalId,
      String url,
      String title,
      String companyName,
      String jobFamily,
      Integer experienceMin,
      Integer experienceMax,
      String employmentType,
      String region,
      LocalDate postedStartDate,
      LocalDate postedEndDate,
      String firstSeenMonth,
      String sampledMonth,
      Long salaryMin,
      Long salaryMax,
      Long salaryBandMin,
      Long salaryBandMax,
      String salaryDisclosure,
      List<String> skills,
      String collectedVia,
      String sourceJobTags,
      Instant collectedAt) {}
}
