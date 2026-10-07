package com.carix.jobposting.jumpit;

import java.util.LinkedHashMap;
import java.util.Map;

/** 점핏 수집 작업 요약. droppedSkills 는 사전에 없어 버린 기술 스택의 빈도(사전 보강 근거), listFailed 는 목록 응답을 못 받아 순회가 끊겼음. */
public record JumpitIngestSummary(
    int pages,
    int fetched,
    int created,
    int enriched,
    int skipped,
    int errors,
    Map<String, Integer> droppedSkills,
    boolean listFailed) {

  public Map<String, Object> toDetails() {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("pages", pages);
    d.put("fetched", fetched);
    d.put("created", created);
    d.put("enriched", enriched);
    d.put("skipped", skipped);
    d.put("errors", errors);
    d.put("droppedSkills", droppedSkills);
    d.put("listFailed", listFailed);
    return d;
  }
}
