package com.carix.pipeline.dto;

import com.carix.pipeline.health.HealthVerdict;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 관리자 데이터 상태 점검 결과. 설계: docs/superpowers/specs/2026-10-08-admin-data-revamp-design.md §2. */
public record DataHealthReport(
    OffsetDateTime evaluatedAt,
    String referenceMonth,
    String previousMonth,
    String engineRuleVersion,
    Summary summary,
    List<Check> checks) {

  public record Summary(int users, int fullyAnswered, List<AnswerGap> gaps) {}

  public record AnswerGap(String answer, String reason, int users) {}

  public record Check(
      String id,
      String title,
      HealthVerdict verdict,
      String message,
      Integer impactUsers,
      String action,
      String actionLink,
      Map<String, Object> evidence,
      String drillDown) {

    /** 원본 링크를 모르는 점검. 서비스가 조립 뒤 {@code DataHealthService} 에서 채운다. */
    public Check(
        String id,
        String title,
        HealthVerdict verdict,
        String message,
        Integer impactUsers,
        String action,
        String actionLink,
        Map<String, Object> evidence) {
      this(id, title, verdict, message, impactUsers, action, actionLink, evidence, null);
    }
  }
}
