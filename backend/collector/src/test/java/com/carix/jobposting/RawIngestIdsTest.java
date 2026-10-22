package com.carix.jobposting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RawIngestIdsTest {

  @Test
  void appendsTheCollectionDateToAListpageId() {
    assertEquals(
        "listpage-1-20260821",
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
