package com.carix.jobposting.jumpit;

import java.util.LinkedHashMap;
import java.util.Map;

/** 점핏 수집 작업 요약. droppedSkills 는 사전에 없어 버린 기술 스택의 빈도(사전 보강 근거). */
public record JumpitIngestSummary(
    int pages,
    int fetched,
    int created,
    int enriched,
    int skipped,
    Map<String, Integer> droppedSkills) {

  public Map<String, Object> toDetails() {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("pages", pages);
    d.put("fetched", fetched);
    d.put("created", created);
    d.put("enriched", enriched);
    d.put("skipped", skipped);
    d.put("droppedSkills", droppedSkills);
    return d;
  }
}
