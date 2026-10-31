package com.carix.jobposting.programmers;

import java.util.LinkedHashMap;
import java.util.Map;

/** 프로그래머스 정기 수집 요약. */
public record ProgrammersCrawlSummary(
    int pages,
    int fetched,
    int created,
    int seen,
    int skipped,
    int errors,
    Map<String, Integer> droppedSkills,
    boolean listFailed) {

  public Map<String, Object> toDetails() {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("pages", pages);
    d.put("fetched", fetched);
    d.put("created", created);
    d.put("seen", seen);
    d.put("skipped", skipped);
    d.put("errors", errors);
    d.put("droppedSkills", droppedSkills);
    d.put("listFailed", listFailed);
    return d;
  }
}
