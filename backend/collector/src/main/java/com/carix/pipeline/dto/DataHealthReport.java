package com.carix.pipeline.dto;

import com.carix.pipeline.health.HealthVerdict;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 관리자 데이터 상태 점검 결과. 설계: docs/superpowers/specs/2026-10-05-admin-data-health-design.md §4. */
public record DataHealthReport(
    OffsetDateTime evaluatedAt,
    String referenceMonth,
    String previousMonth,
    String engineRuleVersion,
    Summary summary,
    List<Group> groups) {

  public record Summary(
      int users,
      int fullyAnswered,
      List<AnswerGap> gaps,
      Map<HealthVerdict, Integer> verdictCounts) {}

  public record AnswerGap(String answer, String reason, int users) {}

  public record Group(String id, String title, String question, List<Check> checks) {}

  public record Check(
      String id,
      String title,
      HealthVerdict verdict,
      String message,
      Integer impactUsers,
      String action,
      String actionLink,
      Map<String, Object> evidence) {}
}
