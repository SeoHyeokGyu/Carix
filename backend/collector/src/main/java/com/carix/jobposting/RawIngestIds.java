package com.carix.jobposting;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * `raw_ingest.source_item_id` 를 짓는다.
 *
 * <p>목록페이지 rawId 는 회차마다 `listpage-1`..`listpage-10` 으로 같다. 회차를 구분하지 않으면 두 번째 크롤의 목록 HTML 이 (source,
 * source_item_id) 유니크 제약에 걸려 저장되지 않고, 그 목록에서만 나오는 공고들이 원본 없이 `job_posting` 에만 남는다. 수집 날짜를 붙여 회차를
 * 가른다.
 *
 * <p>상세페이지(`detail-{externalId}`)는 대상이 아니다 — 이미 공고마다 유일하고, 날짜를 붙이면 같은 공고의 원본이 회차마다 쌓인다.
 */
public final class RawIngestIds {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

  private RawIngestIds() {}

  /** 날짜 경계는 `collected_at` 저장 기준과 같게 UTC 로 자른다. */
  public static String datedListpage(String listpageId, Instant collectedAt) {
    return listpageId + "-" + DATE.format(collectedAt.atOffset(ZoneOffset.UTC));
  }
}
