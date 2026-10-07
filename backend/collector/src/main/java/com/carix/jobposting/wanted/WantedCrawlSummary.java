package com.carix.jobposting.wanted;

import java.util.LinkedHashMap;
import java.util.Map;

/** 원티드 정기 수집 요약. seen 은 이미 있어 관측월만 갱신한 공고. */
public record WantedCrawlSummary(
    int pages,
    int fetched,
    int created,
    int seen,
    int skipped,
    int errors,
    Map<String, Integer> droppedSkills) {

  public Map<String, Object> toDetails() {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("pages", pages);
    d.put("fetched", fetched);
    d.put("created", created);
    d.put("seen", seen);
    d.put("skipped", skipped);
    d.put("errors", errors);
    d.put("droppedSkills", droppedSkills);
    return d;
  }
}
