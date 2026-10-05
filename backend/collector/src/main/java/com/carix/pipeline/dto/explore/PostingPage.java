package com.carix.pipeline.dto.explore;

import java.time.LocalDate;
import java.util.List;

/** 관리자 수집 데이터 — 공고 목록. 연봉 범위는 싣지 않는다. 설계 §5.2. */
public record PostingPage(long total, int page, int size, List<Item> items) {

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
      List<String> skills) {}
}
