package com.carix.jobposting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RawIngestIdsTest {

  @Test
  void appendsTheCollectionDateToAListpageId() {
    assertEquals(
        "listpage-1-20260821",
        RawIngestIds.datedListpage("listpage-1", Instant.parse("2026-08-21T01:15:07Z")));
  }

  /**
   * 이것이 고치려는 결함이다. 목록페이지 rawId 는 회차마다 `listpage-1`..`listpage-10` 으로 같아서, 두 번째 크롤의 목록 HTML 이
   * `raw_ingest` 의 (source, source_item_id) 유니크 제약에 걸려 조용히 버려졌다. 그 목록에서만 나오는 공고들은 원본 없이
   * `job_posting` 에만 남는다.
   */
  @Test
  void separatesTheSamePageCollectedInDifferentCrawls() {
    assertNotEquals(
        RawIngestIds.datedListpage("listpage-1", Instant.parse("2026-08-09T13:32:33Z")),
        RawIngestIds.datedListpage("listpage-1", Instant.parse("2026-08-21T01:15:07Z")));
  }

  /** 같은 크롤 안에서는 시각이 페이지마다 달라도 같은 날짜로 묶인다 — 재적재가 멱등해야 한다. */
  @Test
  void keepsOnePageStableAcrossTheSameCrawl() {
    assertEquals(
        RawIngestIds.datedListpage("listpage-3", Instant.parse("2026-08-21T01:15:10Z")),
        RawIngestIds.datedListpage("listpage-3", Instant.parse("2026-08-21T01:15:23Z")));
  }

  /** 날짜 경계는 UTC 로 자른다. `collected_at` 이 UTC 로 저장되는 것과 같은 기준이다. */
  @Test
  void cutsTheDateBoundaryInUtc() {
    assertEquals(
        "listpage-1-20260820",
        RawIngestIds.datedListpage("listpage-1", Instant.parse("2026-08-20T23:59:59Z")));
  }
}
